// Admin > Actions — metadata_name: events, condition: PERSIST, phase: POST.
// Stored body must stay under 1000 chars (actions.body is varchar(1000)): paste from the line below.
// new event mail; env SNELLO_NOTIFY_EVENTS_TO / SNELLO_PUBLIC_URL override TO / BASE
var TO = 'fiorenzo@example.com', BASE = 'https://api.sne.lol';
var env = java.lang.System.getenv();
var to = env.get('SNELLO_NOTIFY_EVENTS_TO') || TO;
var base = (env.get('SNELLO_PUBLIC_URL') || BASE).replace(/\/+$/, '');
function esc(v) { return v == null ? '' : String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
var name = map.get('name'), begin = map.get('begin_date');
var url = base + '/api/events/' + encodeURIComponent(String(key));
var rcpt = new java.util.ArrayList();
String(to).split(',').forEach(function (r) { if (r.trim()) rcpt.add(r.trim()); });
log.infof('new event key=%s name=%s begin=%s -> %s', key, name, begin, rcpt);
mail.send('New event: ' + name, 'we added a new event: <b>' + esc(name) + '</b> ' + esc(begin)
    + '<br>link to review <a href="' + esc(url) + '">' + esc(url) + '</a>', rcpt, null, null, null);
log.infof('new event mail sent for %s', key);
