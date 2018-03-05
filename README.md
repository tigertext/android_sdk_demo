---
title: "Quick Start Guide"
excerpt: "A quick guide to the TigerConnect Android SDK."
---
The Android SDK is a quick way to integrate secure messaging within a native Android app. This quick start guide will help you get the SDK added to your project.
[block:callout]
{
  "type": "info",
  "body": "We are working to put together a Android demo app but in the meantime, if you have any questions, don't hesitate to [Contact Us](mailto:developersupport@tigertext.com?Subject=Android%20SDK%20Help).  \n\nContact TigerText to Check out the SDK",
  "title": "Do you have a Android demo app?"
}
[/block]

[block:api-header]
{
  "type": "basic",
  "title": "Installation"
}
[/block]
To setup and initiate the Android SDK, follow the following setup steps.  First, download the latest version TigerConnect Android SDK. The SDK itself is not open source, contact TigerText to get access to the SDK

In your project's base directory, create a folder called ttandroid and place the ttandroid.aar inside. Then add a build.gradle file with:
[block:code]
{
  "codes": [
    {
      "code": "configurations.create(\"default\")\nartifacts.add(\"default\", file('ttandroid-3.0.1.aar'))",
      "language": "java"
    }
  ]
}
[/block]
Your directory structure should then look something like the following:
[block:code]
{
  "codes": [
    {
      "code": "build.gradle\nsettings.gradle\n...\nYOUR_APP/\n         build.gradle\n         src/\n         ...\nttandroid/\n          ttandroid.aar\n          build.gradle",
      "language": "java"
    }
  ]
}
[/block]
Next, add the ttandroid module to your settings.gradle.  Note: you only need to add the *':ttandroid', your settings.gradle should already be including your application module.*
[block:code]
{
  "codes": [
    {
      "code": "include ':app', ':ttandroid'",
      "language": "java"
    }
  ]
}
[/block]
Until we publish to an artifact repository soon, you will need to manually add the dependencies of the ttandroid.aar since AAR's don't (by default) come with their dependencies bundled.

Open your app's build.gradle (YOUR_APP/build.gradle in this case) and add these lines, this includes the instruction for compiling the 'ttandroid.aar' as well as the items for multidexing (as it will be necessary).  You will also need some flavor of the support v4 library.
[block:code]
{
  "codes": [
    {
      "code": "android {\n    ...\n\n    defaultConfig {\n        ...\n        multiDexEnabled true\n    }\n    dexOptions {\n        incremental false\n        javaMaxHeapSize \"4g\"\n    }\n}\n\ndependencies {\n    ...\n    implementation 'com.github.heremaps:oksse:master-SNAPSHOT'\n    def boltsVersion = '1.4.0'\n    compile \"com.parse.bolts:bolts-tasks:$boltsVersion\"\n    compile \"com.parse.bolts:bolts-applinks:$boltsVersion\"\n    compile \"com.google.firebase:firebase-messaging:11.8.0\"\n    compile 'net.zetetic:android-database-sqlcipher:3.5.9'\n    compile 'com.squareup.retrofit2:retrofit:2.3.0'\n    compile 'com.jakewharton.timber:timber:4.6.0'\n    def stethoVersion = '1.5.0'\n    compile \"com.facebook.stetho:stetho:$stethoVersion\"\n    compile \"com.facebook.stetho:stetho-okhttp3:$stethoVersion\"\n}",
      "language": "java"
    }
  ]
}
[/block]
Your project should now be able to compile with everything set up.
[block:api-header]
{
  "type": "basic",
  "title": "Initialization"
}
[/block]
##MultiDex
Since you will likely be needing to turn have multidex on (as described above), the lease pervasive way to get multidex going with your application is to add the code below to your Application class.
[block:code]
{
  "codes": [
    {
      "code": "public class MyApplication extends MultiDexApplication {\n  ...\n}",
      "language": "java"
    }
  ]
}
[/block]
There are other ways to go about MultiDex, all of which can be found [here](http://developer.android.com/tools/building/multidex.html).

##Initialize the SDK
1.  Create an Application class in your project.
2.  Instantiate TT.java in Application class’s onCreate() method.
[block:code]
{
  "codes": [
    {
      "code": "public class MyApplication extends MultiDexApplication {\n\n    @Override\n    public void onCreate() {\n        super.onCreate();\n        TT.init(getApplicationContext(), \"your.application.package\");\n    }\n}",
      "language": "java"
    }
  ]
}
[/block]
At this point the SDK is initialized and ready to be used, this initialization step has to be executed before taking any actions on the managers.
[block:api-header]
{
  "type": "basic",
  "title": "Logging In"
}
[/block]
Once the Android SDK is initialized, you will be able to login using the TigerTextAccountManager.  Note: Remember not to use the manager directly, but to leverage the instance as shown in the comments below.
[block:code]
{
  "codes": [
    {
      "code": "...\n// get username (String) and password(String) via input\n...\n\npublic void doLogin(String username, String password) {\n  // Always get the manager through TT.getInstance() instead of TigerTextAccountManager.getInstance()\n    TigerTextAccountManager accountManager = TT.getInstance().getAccountManager();\n        accountManager.login(\"username\", \"password\", new LoginResultCallback(this));\n}\n\n//Like most SDKs, TT's SDK holds strong references to the listeners, so, get a weak reference to your components when necessary...\nprivate static class LoginResultCallback implements LoginListener {\n        private WeakReference<Activity> weakLoginActivity;\n  \n        public LoginResultCallback(Activity loginActivity) {\n            weakLoginActivity = new WeakReference(loginActivity);\n        }\n        @Override\n        public void onLoggedIn(User user) {\n            Activity loginActivity = weakLoginActivity.get();\n            if (loginActivity == null) return;\n\t\t\t\t\t\t//Handle logged in user\n        }\n\n        @Override\n        public void onLoginError(Throwable throwable) {\n            Activity loginActivity = weakLoginActivity.get();\n            if (loginActivity == null) return;\n            //Handle error\n        }\n}",
      "language": "java"
    }
  ]
}
[/block]
After logging in and before interacting further with the SDK, you will want to sync data with TigerText's backend.  Syncing pulls down everything necessary for the account logged in to start interacting fully with the user's organizations, inbox, conversations, etc.
[block:code]
{
  "codes": [
    {
      "code": "TT.getInstance().sync(new TT.SyncListener() {\n    @Override\n    public void onSyncComplete() {\n        // sync successful, continue using the sdk, at this point you can get all the conversations for this user (example available in the next sections)\n    }\n\n    @Override\n    public void onSyncFailed(Throwable throwable) {\n        //otherwise you will want to prompt the user to try and re-sync again\n    }\n});",
      "language": "java",
      "name": null
    }
  ]
}
[/block]

[block:api-header]
{
  "title": "Realtime Updates"
}
[/block]
Getting realtime pull / push going is pretty straightforward. Simply add TT.getInstance().startService() and TT.getInstance().stopService(); calls at the times that your application comes to the foreground (after a user is authenticated of course) and when the application goes into the background(if necessary). One way to achieve this is via [Activity Lifecycle Callbacks] (http://developer.android.com/reference/android/app/Application.ActivityLifecycleCallbacks.html) registered within your Application class, for example:
[block:code]
{
  "codes": [
    {
      "code": "public class RealTimeEventsTracker implements ActivityLifecycleCallbacks {\n\n  private static final int ACTIVITIES_REQUIRED_TO_START_SERVICE = 1;\n    private static final int ACTIVITIES_REQUIRED_TO_STOP_SERVICE = 0;\n  private int mActiveActivities;\n  \n    public void onActivityStarted(Activity activity) {\n        ++mActiveActivities;\n        if (TT.getInstance().getAccountManager().isLoggedIn() && mActiveActivities >= ACTIVITIES_REQUIRED_TO_START_SERVICE) {\n\t\t\t\t\t\tstartSSEService();\n        }\n    }\n\n    public void onActivityStopped(Activity activity) {\n        --mActiveActivities;\n      //Most application don't need to stop the service, because you might want to get updates for your messages even after the app is backgrounded, but if for any reason you have to stop real time updates, this is the way to do it...\n\t\t\t\tif (mActiveActivities == ACTIVITIES_REQUIRED_TO_STOP_SERVICE) {\n\t\t\t\t\t\tstopSSEService();\n\t\t\t\t}\n    }\n\n    public void startSSEService() {\n\t\t\t\tif (TTService.isServiceRunning()) return;\n\t\t\t\ttry {\n\t\t\t\t\t\tTT.getInstance().startService();\n\t\t\t\t} catch (IllegalStateException e) {\n\t\t\t\t\t\tTimber.e(e, \"Failed to start service...\");\n\t\t\t\t}\n\t\t}\n\n    public void stopSSEService() {\n        if (TTService.isServiceRunning()) {\n            TT.getInstance().stopService();\n        }\n    }\n}",
      "language": "java"
    }
  ]
}
[/block]

[block:api-header]
{
  "type": "basic",
  "title": "Logging Out"
}
[/block]
To stop receiving messages for a particular user and to cut off the connection with the server, be sure to log out of the Android SDK.  While the platform does a good job of cleaning up open resources, it is always good housekeeping to logout when the client does not want to show incoming messages.  
[block:code]
{
  "codes": [
    {
      "code": "TT.getInstance().getAccountManager().logout(new LogoutListener() {\n                @Override\n                public void onLoggedOut() {\n                    //Handle logout\n                }\n\n                @Override\n                public void onLogoutError(Throwable throwable) {\n                    //Handle logout Error\n                }\n            });",
      "language": "java"
    }
  ]
}
[/block]
In the method "onLoggedOut" you have the chance to release all the resources linked to the user that was logged into the app...
[block:api-header]
{
  "type": "basic",
  "title": "Getting Inbox"
}
[/block]
After authenticating, the SDK will fetch all the recent conversations associated with the user and persist them in our local datastore.  Assuming that the SDK has been properly initialized and “synced”, this is the way you load your roster.

1.  Call getInboxEntries method of the RosterManager to return the list of all the available conversations.
[block:code]
{
  "codes": [
    {
      "code": "...\n//After your SDK has been sync'ed you can call...\nTT.getInstance().getRosterManager().getInboxEntries(\"organizationId\", new GenericRosterListener() {\n                @Override\n                public void onResults(List<RosterEntry> list) {\n\t\t\t\t\t\t\t\t\t\t//Populate your roster...\n                  \t//Will register for events in the next step...\n                }\n            });",
      "language": "java"
    }
  ]
}
[/block]
2.  In order to keep your roster list up to date after loading the items, you need to subscribe to some roster related events that the SDK will fire when actions like "Roster Added", "Roster Removed", "Roster Updated", etc take place, in addition to the example above notice the last line in the "onResults" method below.
[block:code]
{
  "codes": [
    {
      "code": "...\n//Declare roster related events required...\n    private static final String[] rosterListeners = new String[]{\n            TTEvent.ROSTER_REMOVED,\n            TTEvent.ROSTER_UPDATED,\n            TTEvent.ROSTER_CREATED\n    };\n...\n//After your SDK has been sync'ed you can call...\nTT.getInstance().getRosterManager().getInboxEntries(\"organizationId\", new GenericRosterListener() {\n                @Override\n                public void onResults(List<RosterEntry> list) {\n\t\t\t\t\t\t\t\t\t//Populate your roster...\n                  \n                  //Subscribe to roster related events...\n                  TTPubSub.getInstance().addListeners(RosterActivity.this, rosterListeners);\n                }\n            });",
      "language": "java"
    }
  ]
}
[/block]
3.  Now that we have our roster loaded and we are ready to start listening for roster related events, you are all set for a completely functional roster screen, below is the example of how your RosterActivity might look like.
[block:code]
{
  "codes": [
    {
      "code": "public class RosterActivity extends AppCompatActivity implements TTPubSub.Listener {\n    //Declare the roster events required...\n    private static final String[] rosterListeners = new String[]{\n            TTEvent.ROSTER_REMOVED,\n            TTEvent.ROSTER_UPDATED,\n            TTEvent.ROSTER_CREATED\n    };\n\n    ...\n\n    private void loadRosterAndRegisterForEvents() {\n        TT.getInstance().getRosterManager().getInboxEntries(\"organizationId\", new GenericRosterListener() {\n            @Override\n            public void onResults(List<RosterEntry> list) {\n                //Populate recycler view roster elements...\n              \n                //Start listening for roster related events\n                TTPubSub.getInstance().addListeners(RosterActivity.this, rosterListeners);\n            }\n        });\n    }\n\n    //This method will be called asynchronously every time the SDK fires an event related to the actions you registered on your pubsub...\n    @Override\n    public void onEventReceived(String event, Object data) {\n        switch (event) {\n            case TTEvent.ROSTER_REMOVED: {\n                final RosterEntry rosterEntry = (RosterEntry) data;\n                runOnUiThread(new Runnable() {\n                    @Override\n                    public void run() {\n                        //Do something with the removed roster entry (delete it from the list?)\n                    }\n                });\n            }\n            break;\n            case TTEvent.ROSTER_CREATED: {\n                final RosterEntry rosterEntry = (RosterEntry) data;\n                runOnUiThread(new Runnable() {\n                    @Override\n                    public void run() {\n                        //Do something with the newly created roster entry (add it to the list?)\n                    }\n                });\n            }\n            break;\n                ...//More events...\n        }\n    }\n\n    //When your activity is destroyed don't forget to stop listening for events...\n    @Override\n    protected void onDestroy() {\n        super.onDestroy();\n        TTPubSub.getInstance().removeListeners(RosterActivity.this, rosterListeners);\n    }\n}",
      "language": "java"
    }
  ]
}
[/block]
4.- Loading the messages in a conversation is very similar to the example above, the only difference is that you would be using the ConversationManager to get the messages (paginated) and the events you will have to subscribe to will be message related events, like "New Message", "Message Recalled", "Message Failed", etc, The list of events fired by the SDK can be found in the TTEvent class.
[block:api-header]
{
  "type": "basic",
  "title": "Sending a Message"
}
[/block]
There are several ways to send a message. The following snippet is one way to send a message. 
[block:code]
{
  "codes": [
    {
      "code": "public void sendMessage() {\n        Message messageForSend =  Message.messageForSend(\"Hello World!\", 0, \n                rosterEntry, organizationTTL, null, null, deleteOnRead);\n        TT.getInstance().getConversationManager().sendMessage(messageForSend);\n        \n}",
      "language": "java"
    }
  ]
}
[/block]
Note: RosterEntry is the person/group you are trying to send the message to, TTL is the time-to-live for the message, usually this time is specified by an organization setting.
[block:api-header]
{
  "title": "Sending an Attachment"
}
[/block]
Sending an attachment is very similar to sending a normal message, the difference is that a File object is required along with a mimeType of the attachment that you want to send.
[block:code]
{
  "codes": [
    {
      "code": "public void sendAttachment() {\n        Message attachmentForSend =  Message.messageForSend(\"Hello World!\", 0,\n                rosterEntry, organizationTTL, photoFile, AttachmentMimeTypes.TYPE_JPG, deleteOnRead);\n  \nTT.getInstance().getConversationManager().sendAttachment(attachmentForSend, shouldDeleteAttachment);\n}",
      "language": "java"
    }
  ]
}
[/block]
Sending a message/attachments is as simple as this, after your message is sent, you will start receiving message related events from the Pubsub pipeline, so, make sure you register for events like Message Delivered, Message Read etc.