// Admin > Scheduled Actions
//   name: events-upcoming-mail   frequency: 0 8 * * 1   enabled: true
// Every Monday at 08:00 (Europe/Rome) mails the upcoming events with two attachments:
//   events-upcoming-<date>.html (the table as a page) and events-upcoming-<date>.csv (opens in Excel).
//
// Data comes from the SelectQuery "events_upcoming" over REST (GET /api/events_upcoming), like
// events-upcoming-report: change the query SQL and the mail follows. Attachments are built in memory with
// io.quarkus.mailer.Attachment(name, bytes, contentType) and passed to mail.send(subject, html, to, cc, bcc, attachments).

var BASE = 'https://api.sne.lol';
var QUERY = 'events_upcoming';
var TO = ['fiorenzino@gmail.com', 'mojan123@gmail.com'];
var now = java.time.LocalDateTime.now(java.time.ZoneId.of('Europe/Rome')); // pod JVM is UTC
var day = now.toLocalDate().toString();

var url = BASE + '/api/' + QUERY + '?_sort=' + encodeURIComponent('begin_date asc') + '&_limit=500';
log.infof('upcoming mail: GET %s', url);
var res = call(GET, url);
log.infof('upcoming mail: status=%s ok=%s', res.status(), res.ok());
if (!res.ok()) {
    throw new Error('GET ' + QUERY + ' failed: status=' + res.status() + ' error=' + res.error());
}
var rows = res.result();
if (rows == null || typeof rows.length !== 'number') {
    throw new Error('GET ' + QUERY + ' did not return a JSON array: ' + String(res.body()).substring(0, 200));
}
log.infof('upcoming mail: %s events from select query %s', rows.length, QUERY);

var COLS = ['begin_date', 'end_date', 'name', 'city', 'nation', 'organizer', 'uuid'];

function esc(v) {
    return v == null ? '' : String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
function csvCell(v) {
    var s = v == null ? '' : String(v);
    return /[",;\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
}

// table shared by mail body and HTML attachment
var table = '<table style="border-collapse:collapse"><tr>';
for (var c = 0; c < COLS.length; c++) table += '<th style="border:1px solid #999;padding:4px 8px">' + COLS[c] + '</th>';
table += '</tr>';
var csv = COLS.join(';') + '\r\n';
for (var i = 0; i < rows.length; i++) {
    var r = rows[i];
    table += '<tr>';
    var line = [];
    for (var j = 0; j < COLS.length; j++) {
        var v = r[COLS[j]];
        var cell = COLS[j] === 'uuid'
            ? '<a href="' + BASE + '/api/events/' + encodeURIComponent(String(v)) + '">' + esc(v) + '</a>'
            : esc(v);
        table += '<td style="border:1px solid #999;padding:4px 8px">' + cell + '</td>';
        line.push(csvCell(v));
    }
    table += '</tr>';
    csv += line.join(';') + '\r\n';
}
table += '</table>';

var page = '<!doctype html><html><head><meta charset="utf-8"><title>Upcoming events ' + day + '</title></head>'
    + '<body style="font-family:sans-serif"><h1>Upcoming events</h1><p>' + rows.length + ' events, generated '
    + now.withNano(0) + ' from select query <code>' + QUERY + '</code></p>' + table + '</body></html>';

var Attachment = Java.type('io.quarkus.mailer.Attachment');
var List = Java.type('java.util.ArrayList');
var attachments = new List();
var htmlBytes = new java.lang.String(page).getBytes('UTF-8');
// BOM so Excel reads the CSV as UTF-8 (accents in city names)
var csvBytes = new java.lang.String('﻿' + csv).getBytes('UTF-8');
attachments.add(new Attachment('events-upcoming-' + day + '.html', htmlBytes, 'text/html'));
attachments.add(new Attachment('events-upcoming-' + day + '.csv', csvBytes, 'text/csv'));
log.infof('upcoming mail: attachments html=%s bytes csv=%s bytes', htmlBytes.length, csvBytes.length);

var to = new List();
for (var k = 0; k < TO.length; k++) to.add(TO[k]);

var body = '<p>Ciao,</p><p>these are the <b>' + rows.length + '</b> upcoming events on snello (' + day + ').'
    + ' The same list is attached as HTML and as CSV (opens in Excel).</p>' + table
    + '<p style="color:#888;font-size:12px">Scheduled action events-upcoming-mail, data from select query '
    + QUERY + '.</p>';

mail.send('Snello: ' + rows.length + ' upcoming events (' + day + ')', body, to, null, null, attachments);
log.infof('upcoming mail: sent to %s with %s attachments', to, attachments.size());
