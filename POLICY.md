# MDM Agent Policy Management

## Overview

The MDM Agent implements a policy-driven configuration system for the Connectivity Lab feature in the Treatment Manager. The system reads local policy files, validates and persists them, and broadcasts configuration changes to other applications.

## Policy Schema

The policy file uses the following JSON schema:

```json
{
  "wifiDirect": {
    "enabled": true,
    "discoveryBackoffSeconds": [1, 2, 4, 8, 16]
  },
  "dataChannel": {
    "heartbeatIntervalSec": 5,
    "maxMisses": 3
  }
}
```

### Field Descriptions

- **wifiDirect.enabled**: Boolean flag to enable/disable Wi-Fi Direct functionality
- **wifiDirect.discoveryBackoffSeconds**: Array of integers defining the backoff sequence for discovery retries (in seconds)
- **dataChannel.heartbeatIntervalSec**: Integer defining the interval between heartbeat messages (in seconds)
- **dataChannel.maxMisses**: Integer defining the maximum number of consecutive missed heartbeats before reconnection

## Storage Approach

### Policy File Location
- **Path**: `/sdcard/Download/mdm_policy.json`
- **Format**: JSON
- **Encoding**: UTF-8

### Persistence Strategy
1. **Content Hashing**: SHA-256 hash is calculated for policy file content to detect changes
2. **SharedPreferences Storage**: 
   - Last valid policy JSON is stored under key `last_policy`
   - Last content hash is stored under key `last_hash`
3. **Fallback Behavior**: If parsing fails, the system retains the previously valid policy
4. **Default Policy**: If no valid policy exists, defaults are applied:
   - Wi-Fi Direct enabled with backoff sequence [1, 2, 4, 8, 16] seconds
   - Data channel with 5-second heartbeat interval and 3 max misses

## Broadcast Contract

### Intent Action
```kotlin
const val ACTION_POLICY_UPDATE = "tech.syncvr.mdm_agent.POLICY_UPDATE"
```

### Intent Extras
- **Key**: `policy_json`
- **Type**: String
- **Content**: JSON-serialized policy configuration

### Broadcast Behavior
- **Frequency**: Every 15 minutes via PeriodicWorkManager
- **Scope**: System-wide broadcast with `FLAG_INCLUDE_STOPPED_PACKAGES`
- **Trigger**: Automatic periodic check + immediate trigger on policy file changes

### Example Broadcast Intent
```kotlin
Intent(ACTION_POLICY_UPDATE).apply {
    putExtra(EXTRA_POLICY_JSON, "{\"wifiDirect\":{\"enabled\":true,\"discoveryBackoffSeconds\":[1,2,4,8,16]},\"dataChannel\":{\"heartbeatIntervalSec\":5,\"maxMisses\":3}}")
    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
}
```

## Implementation Details

### Core Components

1. **PolicyRepository**: Manages policy file reading, validation, and persistence
2. **PolicyBroadcastWorker**: PeriodicWorkManager task that handles policy broadcasting
3. **PolicyBroadcastScheduler**: Manages work scheduling and immediate trigger capabilities

### Error Handling

- **File Not Found**: System continues with last valid policy or defaults
- **Invalid JSON**: Parsing errors are logged, previous valid policy is retained
- **Broadcast Failure**: Failures are logged, work is marked as failed for retry

### Logging

All policy operations are logged with the following tags:
- `PolicyRepository`: File reading, parsing, and persistence operations
- `PolicyBroadcastWorker`: Broadcast operations and scheduling
- `PolicyBroadcastScheduler`: Work scheduling and management

## Usage Instructions

1. **Deploy Policy File**: Place `mdm_policy.json` in `/sdcard/Download/` directory
2. **Install MDM Agent**: Ensure MDM Agent is installed and has device owner permissions
3. **Policy Updates**: Modify the JSON file content to update policies
4. **Verification**: Policy changes will be broadcast within 15 minutes or trigger immediately via debug interface

## Testing

### Manual Testing
1. Create policy file with custom values
2. Install and run MDM Agent
3. Monitor logs for policy loading confirmation
4. Verify broadcast reception in Treatment Manager

### Policy Validation
- Ensure JSON is well-formed
- Verify all required fields are present
- Test with various backoff sequences and intervals
- Validate behavior with malformed JSON (should fallback to previous policy)