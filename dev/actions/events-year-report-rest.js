// Admin > Scheduled Actions
//   name: events-year-report   frequency: 30 23 * * *   enabled: true
// Every day at 23:30 (Europe/Rome) stores this year's events as one HTML document, events-<year>-report.html,
// attached to table "events" (table_key report-<year>), overwritten each day.
//
// REST-only version: no db.*, no documents.write. The script only schedules and authenticates:
//   1. token      POST {oidc}/protocol/openid-connect/token  (client_credentials of the pod's own OIDC client,
//                 read from env: QUARKUS_OIDC_AUTH_SERVER_URL / _CLIENT_ID / _CREDENTIALS_SECRET; never logged)
//   2. lookup     GET  /api/documents?table_name=events&table_key=report-<year>   (is there a report already?)
//   3. store      PUT  /api/documents/{uuid}  or  POST /api/documents   (multipart, no file part, only
//                 remote_url = the print template URL: snello downloads the rendered HTML itself)
// The data + layout live in Admin > Print Templates > events_year_report (SQL + Qute HTML), editable without
// touching this script.
//
// Needs: the OIDC client's service account must have a write role (Admin / Manager / User) in its roles claim.

var BASE = 'https://api.sne.lol';
var TEMPLATE = 'events_year_report';
var env = java.lang.System.getenv();

// The pod's JVM runs in UTC: take "now" in Rome time
var now = java.time.LocalDateTime.now(java.time.ZoneId.of('Europe/Rome'));
var year = now.getYear();
var tableKey = 'report-' + year;
var fileName = 'events-' + year + '-report.html';
log.infof('events report: year=%s table_key=%s template=%s', year, tableKey, TEMPLATE);

// --- 1. service-account token -------------------------------------------------------------------------------
var oidc = String(env.get('QUARKUS_OIDC_AUTH_SERVER_URL') || '').replace(/\/+$/, '');
var clientId = env.get('QUARKUS_OIDC_CLIENT_ID');
var clientSecret = env.get('QUARKUS_OIDC_CREDENTIALS_SECRET');
if (!oidc || !clientId || !clientSecret) {
    throw new Error('OIDC env missing: auth-server-url=' + !!oidc + ' client-id=' + !!clientId + ' secret=' + !!clientSecret);
}
var tok = call(POST, oidc + '/protocol/openid-connect/token',
    'grant_type=client_credentials&client_id=' + encodeURIComponent(clientId)
    + '&client_secret=' + encodeURIComponent(clientSecret),
    {'Content-Type': 'application/x-www-form-urlencoded'});
log.infof('events report: token request client=%s status=%s', clientId, tok.status());
if (!tok.ok()) {
    throw new Error('token request failed: status=' + tok.status() + ' ' + String(tok.body()).substring(0, 200));
}
var AUTH = {'Authorization': 'Bearer ' + tok.result().access_token};

// --- 2. existing report document? --------------------------------------------------------------------------
var found = call(GET, BASE + '/api/documents?table_name=events&table_key=' + encodeURIComponent(tableKey) + '&_limit=1', null, AUTH);
if (!found.ok()) {
    throw new Error('documents lookup failed: status=' + found.status());
}
var existing = found.result();
var docUuid = existing && existing.length > 0 ? existing[0].uuid : null;
log.infof('events report: existing document=%s', docUuid);

// --- 3. store: snello fetches the rendered template from remote_url -----------------------------------------
var printUrl = BASE + '/api/printtemplates/' + TEMPLATE + '/print'
    + '?begin_date_gte=' + year + '-01-01&begin_date_lt=' + (year + 1) + '-01-01'
    + '&_sort=' + encodeURIComponent('begin_date:asc');
log.infof('events report: remote_url=%s', printUrl);

// Pre-flight: the remote_url download in /api/documents does not check the HTTP status, so a template error
// (400 {"error": ...}) would be stored as the report and overwrite the good one. Render once here and stop on error.
var probe = call(GET, printUrl);
var probeBody = String(probe.body() || '');
log.infof('events report: template pre-flight status=%s length=%s', probe.status(), probeBody.length);
if (!probe.ok() || probeBody.indexOf('<html') < 0) {
    throw new Error('print template ' + TEMPLATE + ' failed: status=' + probe.status() + ' ' + probeBody.substring(0, 300));
}

var boundary = 'snello' + java.util.UUID.randomUUID().toString().replace(/-/g, '');
function part(name, value) {
    return '--' + boundary + '\r\nContent-Disposition: form-data; name="' + name + '"\r\n\r\n' + value + '\r\n';
}
var form = part('table_name', 'events') + part('table_key', tableKey)
    + part('filename', fileName) + part('original_name', fileName) + part('mimeType', 'text/html')
    + part('remote_url', printUrl) + '--' + boundary + '--\r\n';
var headers = {'Authorization': AUTH.Authorization, 'Content-Type': 'multipart/form-data; boundary=' + boundary};

var saved = docUuid
    ? call(PUT, BASE + '/api/documents/' + encodeURIComponent(docUuid), form, headers)
    : call(POST, BASE + '/api/documents', form, headers);
log.infof('events report: %s document status=%s', docUuid ? 'PUT' : 'POST', saved.status());
if (!saved.ok()) {
    throw new Error('store document failed: status=' + saved.status() + ' ' + String(saved.body()).substring(0, 300));
}
var doc = saved.result();
log.infof('events report: stored uuid=%s path=%s size=%s', doc.uuid, doc.path, doc.size);
