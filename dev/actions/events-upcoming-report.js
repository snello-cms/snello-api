// Admin > Scheduled Actions
//   name: events-upcoming-report   frequency: 0 7 * * *   enabled: true
// Every day at 07:00 (Europe/Rome) writes the upcoming events into one HTML document, events-upcoming-report.html,
// attached to table "events" (overwritten each run).
//
// Twin of events-year-report, but it reads NO table directly: the data comes from the SelectQuery
// "events_upcoming" (Admin > Select Queries) called over REST with the script's http client, exactly like any
// external client would: GET /api/events_upcoming. Change the SQL of the select query and this report follows,
// without touching the script.
//
// Bindings used: call(GET, url) (prelude over `http`), documents, db (only to register the documents row), log.

var BASE = 'https://api.sne.lol';
var QUERY = 'events_upcoming';
var uuid = 'events-upcoming-report';            // fixed -> overwrite
var fileName = 'events-upcoming-report.html';
// The pod's JVM runs in UTC: take "now" in Rome time, or the report says 13:37 for 14:37
var now = java.time.LocalDateTime.now(java.time.ZoneId.of('Europe/Rome'));

// _sort / _limit are standard snello list params; the select query has with_params=true, so column filters
// (e.g. &nation=Italy or &begin_date_lt=2027-01-01) can be appended too.
var url = BASE + '/api/' + QUERY + '?_sort=' + encodeURIComponent('begin_date asc') + '&_limit=500';
log.infof('upcoming report: GET %s', url);

var res = call(GET, url);
log.infof('upcoming report: status=%s ok=%s', res.status(), res.ok());
if (!res.ok()) {
    // throw -> the scheduled action is marked failed with this message (visible in Admin > Scheduled Actions)
    throw new Error('GET ' + QUERY + ' failed: status=' + res.status() + ' error=' + res.error());
}

var rows = res.result();                          // JSON array parsed by the prelude
if (rows == null || typeof rows.length !== 'number') {
    throw new Error('GET ' + QUERY + ' did not return a JSON array: ' + String(res.body()).substring(0, 200));
}
log.infof('upcoming report: %s events from select query %s', rows.length, QUERY);

function esc(v) {
    return v == null ? '' : String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

var html = '<!doctype html><html><head><meta charset="utf-8"><title>Upcoming events</title>'
    + '<style>body{font-family:sans-serif}table{border-collapse:collapse}td,th{border:1px solid #999;padding:4px 8px}</style>'
    + '</head><body><h1>Upcoming events</h1>'
    + '<p>' + rows.length + ' events, generated ' + now.withNano(0)
    + ' from select query <code>' + QUERY + '</code> via REST</p>'
    + '<table><tr><th>Begin</th><th>End</th><th>Name</th><th>City</th><th>Nation</th><th>Organizer</th><th>Link</th></tr>';
for (var i = 0; i < rows.length; i++) {
    var r = rows[i];
    var link = BASE + '/api/events/' + encodeURIComponent(String(r.uuid));
    html += '<tr><td>' + esc(r.begin_date) + '</td><td>' + esc(r.end_date) + '</td><td>' + esc(r.name)
        + '</td><td>' + esc(r.city) + '</td><td>' + esc(r.nation) + '</td><td>' + esc(r.organizer)
        + '</td><td><a href="' + link + '">' + esc(r.uuid) + '</a></td></tr>';
}
html += '</table></body></html>';

var bytes = new java.lang.String(html).getBytes('UTF-8');
var stored = documents.write(bytes, uuid, 'events', 'html');
log.infof('upcoming report: stored at %s (%s bytes)', stored.get('path'), bytes.length);

var row = new java.util.HashMap();
row.put('name', fileName); row.put('original_name', fileName); row.put('path', stored.get('path'));
row.put('mimetype', 'text/html'); row.put('size', bytes.length);
row.put('table_name', 'events'); row.put('table_key', 'upcoming-report');

if (db.fetch(null, 'documents', 'uuid', uuid) == null) {
    row.put('uuid', uuid);
    db.create('documents', 'uuid', row);
    log.infof('upcoming report: documents row %s created', uuid);
} else {
    db.update('documents', 'uuid', row, uuid);
    log.infof('upcoming report: documents row %s updated', uuid);
}
