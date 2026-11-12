// Admin > Actions — metadata_name: events, condition: PERSIST, phase: POST (replaces events-new-event-mail:
// only one POST action per table + condition runs).
// Hands the new event to the Quarkus Flow workflow snello:new-event (quarkus-flow/workflows/snello-new-event.yaml),
// which reads the event, mails it through the Brevo HTTP API and retries 5xx/429.
// snello config: snello.http.allow-private=true (the runner is a local / in-cluster address),
// ideally with snello.http.allowed-hosts=<runner host>. Runner URL: env SNELLO_FLOW_URL or FLOW below.
var FLOW = 'http://127.0.0.1:8090';
var base = (java.lang.System.getenv().get('SNELLO_FLOW_URL') || FLOW).replace(/\/+$/, '');
var url = base + '/q/flow/exec/snello/new-event';
log.infof('new event %s -> flow %s', key, url);
var r = call(POST, url, JSON.stringify({ key: String(key) }));
if (!r.ok()) {
    throw 'flow start failed for ' + key + ': status=' + r.status() + ' error=' + r.error() + ' body=' + r.body();
}
var res = r.result();
log.infof('new event %s -> flow instance %s (%s)', key, res && res.instanceId, res && res.status);
