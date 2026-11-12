// Admin > Scheduled Actions
//   name: events-year-report   frequency: 30 23 * * *   enabled: true
// Every day at 23:30 writes this year's events into one HTML document, events-<year>-report.html, attached to
// table "events". The file and its documents row are overwritten each day (one up-to-date report per year,
// not 365 copies). Needs no Print Template and no `prints` binding, so it runs on the current image.

var now = java.time.LocalDateTime.now();
var year = now.getYear();                      // integer: safe to inline in SQL
var uuid = 'events-report-' + year;            // fixed per year -> overwrite
var fileName = 'events-' + year + '-report.html';

log.infof('events report: year=%s uuid=%s', year, uuid);

// SELECT *: columns differ between databases (e.g. no organizer locally); a missing column renders empty
var rows = db.list("SELECT * FROM events"
    + " WHERE begin_date >= '" + year + "-01-01' AND begin_date < '" + (year + 1) + "-01-01'"
    + " ORDER BY begin_date ASC");
log.infof('events report: %s events found', rows.size());

function esc(v) {
    return v == null ? '' : String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

var html = '<!doctype html><html><head><meta charset="utf-8"><title>Events ' + year + '</title>'
    + '<style>body{font-family:sans-serif}table{border-collapse:collapse}td,th{border:1px solid #999;padding:4px 8px}</style>'
    + '</head><body><h1>Events ' + year + '</h1>'
    + '<p>' + rows.size() + ' events, generated ' + now.withNano(0) + '</p>'
    + '<table><tr><th>Begin</th><th>End</th><th>Name</th><th>City</th><th>Organizer</th><th>Link</th></tr>';
for (var i = 0; i < rows.size(); i++) {
    var r = rows.get(i);
    var url = 'https://api.sne.lol/api/events/' + encodeURIComponent(String(r.get('uuid')));
    html += '<tr><td>' + esc(r.get('begin_date')) + '</td><td>' + esc(r.get('end_date')) + '</td><td>' + esc(r.get('name'))
        + '</td><td>' + esc(r.get('city')) + '</td><td>' + esc(r.get('organizer'))
        + '</td><td><a href="' + url + '">' + esc(r.get('uuid')) + '</a></td></tr>';
}
html += '</table></body></html>';

var bytes = new java.lang.String(html).getBytes('UTF-8');
var stored = documents.write(bytes, uuid, 'events', 'html');
log.infof('events report: stored at %s (%s bytes)', stored.get('path'), bytes.length);

var row = new java.util.HashMap();
row.put('name', fileName); row.put('original_name', fileName); row.put('path', stored.get('path'));
row.put('mimetype', 'text/html'); row.put('size', bytes.length);
row.put('table_name', 'events'); row.put('table_key', 'report-' + year);

if (db.fetch(null, 'documents', 'uuid', uuid) == null) {
    row.put('uuid', uuid);
    db.create('documents', 'uuid', row);
    log.infof('events report: documents row %s created', uuid);
} else {
    db.update('documents', 'uuid', row, uuid);
    log.infof('events report: documents row %s updated', uuid);
}
