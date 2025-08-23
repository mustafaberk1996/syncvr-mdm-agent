# SyncVR MDM Agent

This MDM agent is setup as a DeviceOwner to have access to device adminstration APIs such as the
DevicePolicyManager.

As such, it's not exactly a standard app and requires some setup steps, either for production or
development use.

## To set this up locally

* Install Android Studio
* Clone the repository
* Debug builds should compile out-of-the-box even if they won't successfully connect to Firebase
* To switch from release to debug build, you'll have to factory reset.

## MDM setup

### Production

Usually externally handled with our SetupTool, out of scope here.

### Development

#### Device

After a factory reset, you should be able to install debug builds as a normal app.

You'll then need to set it as device owner.

Run those adb commands:

```
adb shell pm grant tech.syncvr.mdm_agent android.permission.READ_PHONE_STATE
adb shell pm grant tech.syncvr.mdm_agent android.permission.PACKAGE_USAGE_STATS
adb shell pm grant tech.syncvr.mdm_agent android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant tech.syncvr.mdm_agent android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant tech.syncvr.mdm_agent android.permission.ACCESS_FINE_LOCATION
adb shell pm grant tech.syncvr.mdm_agent android.permission.ACCESS_BACKGROUND_LOCATION
adb shell dpm set-device-owner tech.syncvr.mdm_agent/.receivers.DeviceOwnerReceiver
```

The last command is the most important one, setting the DeviceOwner. Some permissions
grant may "naturally" fail when not supported by some Android OS versions. MDM
Agent also triggers an auto-grant procedure on the device which should take care
of all permissions needs.

##### Uninstalling a development build of the MDM Agent

```
adb shell dpm remove-active-admin tech.syncvr.mdm_agent/.receivers.DeviceOwnerReceiver && adb uninstall tech.syncvr.mdm_agent
```

This will not work with a production build or a build of type `stagingRelease` and this is intended.

Only builds with `testOnly=true` in AndroidManifest.xml can be uninstalled.

#### Dashboard

Debug builds use the staging environment.

Assuming you already have access there, you also will need to manually add your device in there.

* Lookup your deviceid using `adb devices`
* Set the customer and department name according to your access

## Firebase

Firebase configuration is cached during build. Remember to clean when switching variants.

### Manually

* Firebase contains google-services.json
* Firebase contains a secret to be put in secrets.xml
* Lookup the debug configuration setup and modify it or setup the release configuration the same way

### Automatically

* `./gradlew firebase` will automatically configure all secrets.xml and google-services.json
* But only after you set your Firebase credentials in a .syncvr directory in your home folder
* As well as a gradle.properties file in your gradle home
* Ask Freek or Alexis for the gradle.properties file
* Running the task once without configuration will print exact location for expected .syncvr
  directory as well as gradle home.

## MDM Release

Doesn't account for testing just the part of the process related to infrastructure.

#### Github

The creation of a Github Release object is automated through the release workflow.

The release workflow is triggered when a tag using the format VX.Y.Z is pushed.
Therefore the release workflow is not run manually.

Github Release objects contains multiple APKs and AARs, such as the Platform SDK.

VersionCode and VersionName attributes are set in the APK using the git tag value.

#### SyncVR Platform

Once released on Github, assuming the APK has been tested we need to make it
available to devices for update through SyncVR Platform.

For that to happen, you'll need to create a new release object in the SyncVR
Platform:

* XR Devs
    * SyncVR-devs
        * Platform/MDM Agent
            * Create New release & upload APK from Github

#### SetupTool depends on the MDM

The SetupTool has its own repository to store and access MDM releases for now.
The SetupTool is responsible for provisioning devices straight `out-of-the-box`.

We need to run the `Publish to SetupTool repository` workflow manually, using the
`workflow_dispatch` trigger from GithubActions frontend.

That workflow will fail when run on a branch. It needs to be set on a tag which
already has an existing Github Release object.

Running the workflow will upload the apks from the Github Release object to the
SetupTool repository which is a cloud bucket.


## Proguard/R8

Android team switched from Proguard to R8 in April 2019. They're equivalent pieces of software for
our concernes and R8 re-uses Proguard configuration files and syntax.
https://developer.android.com/studio/releases/gradle-plugin#3-4-0

It's enabled in both release and debug variants.

It should be possible to de-obfuscate a trace using the retrace.sh script which is part of the SDK.
You will need the mapping file for that specific build.

```
retrace.bat|retrace.sh [-verbose] mapping.txt [<stacktrace_file>]
```
