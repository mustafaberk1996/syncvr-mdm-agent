Android Developer Assessment
Welcome
Hello prospective SyncVR-ranger! Thanks for being interested in a position as a developer at
SyncVR! At SyncVR we work hard and with passion on a daily basis to improve the quality of life of
healthcare using Extended Reality.Because we think XR is an awesome technology, and because we
believe it has the most impact when used to take people out oftheir hospital setting where they are
a patient, and transport them to tropic islands or outer space. We are a small team of extremely
motivated, independent and self-starting people, and we're looking for others like us.
In this assignment, you will already get your hands dirty into part of our existing codebase. This will
not only help us assess your skills and approach, but it also helps you understand what we’re
building!
Overview
Goal: show that you can understand and extend our Android stack that governs tablet ↔ headset
connectivity and MDM-style configuration, without needing a physical XR headset.
Timebox: complete within 6 hours (if you don’t finish within 6 hours, please stop and send your
progress anyway).
You will receive two repos: Treatment Manager (Android app that includes our connectivity layer)
and MDM Agent (Android service that applies device policy and configuration). No backend access
is needed.
● Treatment Manager: link
● MDM Agent: link
*Please announce when you want to start the exercise. You will then be granted access to the
codebase files at that time. In your submission of the result, please confirm deleting the local
codebase copies on your device.
**You do not need a VR headset. If you have a spare Android device you may test a P2P connect, but
itis optional. Loopback mode is sufficient
What you will build
A single “Connectivity Lab” feature inside Treatment Manager, plus a small policy hook in MDM
Agent.
Part A. Wi-Fi Direct discovery and state handling (tablet only)
Implement a simple screen named Connectivity Lab that exercises the existing Wi-Fi Directlayer.
SyncVR Medical - Improving healthcare with VR
Requirements
● Start and stop peer discovery using WifiP2pManager.
● Handle runtime permissions correctly: NEARBY_WIFI_DEVICES on Android 13 and above,
ACCESS_FINE_LOCATION on Android 12 and below.
● Implement a minimal state machine for discovery with a capped exponential backoff when
discovery fails or finds zero peers. Suggested backoff sequence:1 s, 2 s, 4 s, 8 s,16 s, cap at
30 s, jitter ±20 percent.
● Render live status in the UI:
○ Idle, Discovering, PeersFound(n), Connecting(peer), Connected, GroupLost,
Error(code).
● Add structured logs for each transition. Make logs easy to filter.
Notes
● Connecting to a peer is optional. If you have a second Android device and wish to verify
connect, you may do so. This is notrequired to pass.
● Build with ASSESSMENT_MODE=true (Gradle property orBuildConfig flag). This disables any
backend/Firebase initialisation and enables the local policy broadcast path
Part B. Local data-channel loopback, heartbeat, and recovery
Add a Loopback Mode toggle in Connectivity Lab.
Requirements
● Start a lightweightin-app WebSocket server on 127.0.0.1:8484.
● Connect a WebSocket clientto that endpoint when Loopback Mode is enabled.
Send a heartbeat JSON message every 5 seconds,for example:
{"type":"ping","sessionId":"<uuid>","seq":123}
● The server should reply with {"type":"pong","at":<timestamp>}. Ifthree heartbeats in a row
time out, reconnectthe client. Do not alterthe Wi-Fi Direct state when Loopback Mode is
active.
● Show counters in the UI: heartbeatsSent, heartbeatsAcked, consecutiveMisses, and current
client connection state.
Purpose
This validates serialisation, scheduling, error handling, and reconnection logic without a second
device.
Part C. MDM-driven policy hook
Wire a tiny policy path from MDM Agent to Treatment Manager so configuration can be updated at
runtime.
SyncVR Medical - Improving healthcare with VR
Requirements
● In MDM Agent, read a local policy file from file:///sdcard/Download/mdm_policy.json. Use
the example below as the schema.
● Persistthe last applied policy and a content hash. If parsing fails, keep the previously valid
policy.
● Implement a periodic WorkManagertask in MDM Agentthat broadcasts the effective
configuration for Connectivity Lab.
● In Treatment Manager, receive the broadcast and apply values atruntime. Reflectthe
currently effective values in the Connectivity Lab UI.
Example policy JSON
{
"wifiDirect": {"enabled":true,"discoveryBackoffSeconds": [1, 2, 4, 8,16] },
"dataChannel": {"heartbeatIntervalSec": 5,"maxMisses": 3 }
}
*Note that you may need to grant certain permissions (e.g. setting a device owner) in your device to
make things work.
Deliverables
1. Code changes in both repos on clearly named feature branches.
2. DESIGN.md in Treatment Manager:
○ Wi-Fi Direct discovery state machine,transitions, and backoff parameters.
○ WebSocket heartbeat and recovery strategy.
3. POLICY.md in MDM Agent:
○ Policy schema, storage approach, and the broadcast contract.
4. Tests
○ One unittestforthe backoff scheduler, including cap and optional jitter.
○ One instrumentation testthat enables Loopback Mode, verifies heartbeats
increment,then simulates a server stop and observes automatic reconnection.
5. Short screen recording (undertwo minutes)that shows:
○ Starting discovery, status updates in the UI, clean retries with backoff.
○ Loopback Mode on, heartbeats counting up,then recovery after you stop and
restartthe local server.
○ MDM policy change applied atruntime,for example heartbeatinterval changes
reflected in the UI
Acceptance criteria
● Connectivity Lab handles permissions, discovery, and error cases without UI jank or crashes.
● Backoff is capped, stops on success, and resets appropriately.
● Loopback clientreconnects after consecutive misses, withouttoggling Wi-Fi Direct.
● Treatment Manager applies policy values atruntime upon receiving the MDM broadcast, and
the UI shows the effective values.
SyncVR Medical - Improving healthcare with VR
● Code is readable,testable, and uses modern Android patterns. Prefer Hiltfor DI, Kotlin
coroutines, and Compose forthe UI.
Constraints and guidance
● Keep the UIto one Compose screen plus any small helpers.
● Do notintroduce heavy third-party frameworks. If a WebSocketlibrary already exists in the
repo, reuse it. Otherwise, a minimal Ktor or OkHttp WebSocketis fine.
● Avoid changing public APIs of shared modules. If you must, documentthe change.
● No backend calls, use only local resources and broadcasts. If you wantto mock any backend
connectivity, you can.
● Make logging helpful, concise, and privacy aware.
How to run it
1. Place mdm_policy.json into the device Downloads folder.
2. Install and run MDM Agent, ensure the periodic worker runs ortrigger itif you include a
debug button.
3. Install and run Treatment Manager, open Connectivity Lab.
4. Grantruntime permissions when asked.
5. Observe discovery status transitions. Toggle Loopback Mode to verify the data channel and
recovery.
6. Edit mdm_policy.json to change intervals,triggerthe MDM Agent worker, and confirm the
new values in the UI.
Evaluation rubric
● Stability and correctness: 40%
● Code quality and architecture: 25%
● Testing:15%
● MDM integration and documentation: 20%
*A note about AI tools: we encourage use of AI tools like ChatGPT or Copilot. During the tech
interview we'll be interested to hear how you used it, but we also expect you to understand all the
code you deliver!
We think that you should be able to finish the above assignment in at most one day. Don't spend
more time on itthan that, even if you're not completely finished!
Delivery
Once the assessment is done, publish it to Github / Bitbucket / Gitlab, and send it over to
floris@syncvr.tech. We'lltake a good look atit and then contact you for a follow up!
Good luck, have fun, and rock & roll!
SyncVR Medical - Improving healthcare with VR