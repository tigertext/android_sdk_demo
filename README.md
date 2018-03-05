
# Quick Start Guide
The Android SDK is a quick way to integrate secure messaging within a native Android app. This quick start guide will help you get the SDK added to your project.

We are working to put together a
Android demo app but in the meantime, if you have any questions, don't
hesitate to [Contact Us](mailto:developersupport@tigertext.comSubject=Android%20SDK%20Help) 
Contact TigerText to Check out the SDK.

## Installation
To setup and initiate the Android SDK, follow the following setup steps.  First, download the latest version TigerConnect Android SDK. The SDK itself is not open source, contact TigerText to get access to the SDK

In your project's base directory, create a folder called ttandroid and place the ttandroid.aar inside. Then add a build.gradle file with:

    configurations.create(\"default\")
    artifacts.add(\"default\", file('ttandroid-3.0.1.aar'))


Your directory structure should then look something like the following:

    build.gradle
    settings.gradle...
    YOUR_APP/
             build.gradle
                      src/
                      ...
    ttandroid/
             ttandroid.aar
             build.gradle

**Next, add the ttandroid module to your settings.gradle.  Note: you only need to add the** *':ttandroid', your settings.gradle should already be including your application module.*

    include ':app', ':ttandroid'

Until we publish to an artifact repository soon, you will need to manually add the dependencies of the ttandroid.aar since AAR's don't (by default) come with their dependencies bundled.

Open your app's build.gradle (YOUR_APP/build.gradle in this case) and add these lines, this includes the instruction for compiling the 'ttandroid.aar' as well as the items for multidexing (as it will be necessary).  You will also need some flavor of the support v4 library.

    android {
        ...
    
        defaultConfig {
            ...
            multiDexEnabled true
        }
        dexOptions {
            incremental false
            javaMaxHeapSize "4g"
        }
    }
    
    dependencies {
        ...
        implementation 'com.github.heremaps:oksse:master-SNAPSHOT'
        def boltsVersion = '1.4.0'
        compile "com.parse.bolts:bolts-tasks:$boltsVersion"
        compile "com.parse.bolts:bolts-applinks:$boltsVersion"
        compile "com.google.firebase:firebase-messaging:11.8.0"
        compile 'net.zetetic:android-database-sqlcipher:3.5.9'
        compile 'com.squareup.retrofit2:retrofit:2.3.0'
        compile 'com.jakewharton.timber:timber:4.6.0'
        def stethoVersion = '1.5.0'
        compile "com.facebook.stetho:stetho:$stethoVersion"
        compile "com.facebook.stetho:stetho-okhttp3:$stethoVersion"
    }

Your project should now be able to compile with everything set up.

## Initialization
### MultiDex
Since you will likely be needing to turn have multidex on (as described above), the lease pervasive way to get multidex going with your application is to add the code below to your Application class.

    public class MyApplication extends MultiDexApplication {
      ...
    }

There are other ways to go about MultiDex, all of which can be found [here](http://developer.android.com/tools/building/multidex.html).

### Initialize the SDK

1.- Create an Application class in your project.
2.- Instantiate TT.java in Application class’s onCreate() method.

    public class MyApplication extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        TT.init(getApplicationContext(), "your.application.package");
        }
    }

At this point the SDK is initialized and ready to be used, this initialization step has to be executed before taking any actions on the managers.

## Logging In

Once the Android SDK is initialized, you will be able to login using the TigerTextAccountManager.  Note: Remember not to use the manager directly, but to leverage the instance as shown in the comments below.

    ...
    // get username (String) and password(String) via input
    ...
    
    public void doLogin(String username, String password) {
      // Always get the manager through TT.getInstance() instead of TigerTextAccountManager.getInstance()
        TigerTextAccountManager accountManager = TT.getInstance().getAccountManager();
            accountManager.login("username", "password", new LoginResultCallback(this));
    }
    
    //Like most SDKs, TT's SDK holds strong references to the listeners, so, get a weak reference to your components when necessary...
    private static class LoginResultCallback implements LoginListener {
        private WeakReference<Activity> weakLoginActivity;
  
        public LoginResultCallback(Activity loginActivity) {
            weakLoginActivity = new WeakReference(loginActivity);
        }
        @Override
        public void onLoggedIn(User user) {
            Activity loginActivity = weakLoginActivity.get();
            if (loginActivity == null) return;
            //Handle logged in user
        }

        @Override
        public void onLoginError(Throwable throwable) {
            Activity loginActivity = weakLoginActivity.get();
            if (loginActivity == null) return;
            //Handle error
        }
    }

After logging in and before interacting further with the SDK, you will want to sync data with TigerText's backend.  Syncing pulls down everything necessary for the account logged in to start interacting fully with the user's organizations, inbox, conversations, etc.

    TT.getInstance().sync(new TT.SyncListener() {
	    @Override
	    public void onSyncComplete() {
	        // sync successful, continue using the sdk, at this point you can get all the conversations for this user (example available in the next sections)
	    }

	    @Override
	    public void onSyncFailed(Throwable throwable) {
	        //otherwise you will want to prompt the user to try and re-sync again
	    }
    });

## Realtime Updates

Getting realtime pull / push going is pretty straightforward. Simply add TT.getInstance().startService() and TT.getInstance().stopService(); calls at the times that your application comes to the foreground (after a user is authenticated of course) and when the application goes into the background(if necessary). One way to achieve this is via [Activity Lifecycle Callbacks](http://developer.android.com/reference/android/app/Application.ActivityLifecycleCallbacks.html) registered within your Application class, for example:

    public class RealTimeEventsTracker implements ActivityLifecycleCallbacks {
    
      private static final int ACTIVITIES\_REQUIRED\_TO\_START\_SERVICE = 1;
        private static final int ACTIVITIES\_REQUIRED\_TO\_STOP\_SERVICE = 0;
      private int mActiveActivities;
      
        public void onActivityStarted(Activity activity) {
            ++mActiveActivities;
            if (TT.getInstance().getAccountManager().isLoggedIn() && mActiveActivities >= ACTIVITIES\_REQUIRED\_TO\_START\_SERVICE) {
                startSSEService();
            }
        }
    
        public void onActivityStopped(Activity activity) {
            --mActiveActivities;
          //Most application don't need to stop the service, because you might want to get updates for your messages even after the app is backgrounded, but if for any reason you have to stop real time updates, this is the way to do it...
            if (mActiveActivities == ACTIVITIES\_REQUIRED\_TO\_STOP\_SERVICE) {
                stopSSEService();
            }
        }
    
        public void startSSEService() {
            if (TTService.isServiceRunning()) return;
            try {
                TT.getInstance().startService();
            } catch (IllegalStateException e) {
                Timber.e(e, "Failed to start service...");
            }
        }
    
        public void stopSSEService() {
            if (TTService.isServiceRunning()) {
                TT.getInstance().stopService();
            }
        }
    }

## Logging Out

To stop receiving messages for a particular user and to cut off the connection with the server, be sure to log out of the Android SDK.  While the platform does a good job of cleaning up open resources, it is always good housekeeping to logout when the client does not want to show incoming messages.  

    TT.getInstance().getAccountManager().logout(new LogoutListener() {
                    @Override
                    public void onLoggedOut() {
                        //Handle logout
                    }
    
                    @Override
                    public void onLogoutError(Throwable throwable) {
                        //Handle logout Error
                    }
                });

In the method "onLoggedOut" you have the chance to release all the resources linked to the user that was logged into the app...

## Getting Inbox

After authenticating, the SDK will fetch all the recent conversations associated with the user and persist them in our local datastore.  Assuming that the SDK has been properly initialized and “synced”, this is the way you load your roster.

1.- Call getInboxEntries method of the RosterManager to return the list of all the available conversations.

    ...
    //After your SDK has been sync'ed you can call...
    TT.getInstance().getRosterManager().getInboxEntries("organizationId", new GenericRosterListener() {
                    @Override
                    public void onResults(List<RosterEntry> list) {
                        //Populate your roster...
                        //Will register for events in the next step...
                    }
                });

2.-  In order to keep your roster list up to date after loading the items, you need to subscribe to some roster related events that the SDK will fire when actions like "Roster Added", "Roster Removed", "Roster Updated", etc take place, in addition to the example above notice the last line in the "onResults" method below.

    ...
    //Declare roster related events required...
        private static final String\[\] rosterListeners = new String\[\]{
                TTEvent.ROSTER_REMOVED,
                TTEvent.ROSTER_UPDATED,
                TTEvent.ROSTER_CREATED
        };
    ...
    //After your SDK has been sync'ed you can call...
    TT.getInstance().getRosterManager().getInboxEntries("organizationId", new GenericRosterListener() {
                    @Override
                    public void onResults(List<RosterEntry> list) {
                      //Populate your roster...
                      
                      //Subscribe to roster related events...
                      TTPubSub.getInstance().addListeners(RosterActivity.this, rosterListeners);
                    }
                });


3.-  Now that we have our roster loaded and we are ready to start listening for roster related events, you are all set for a completely functional roster screen, below is the example of how your RosterActivity might look like.

    public class RosterActivity extends AppCompatActivity implements TTPubSub.Listener {
        //Declare the roster events required...
        private static final String\[\] rosterListeners = new String\[\]{
                TTEvent.ROSTER_REMOVED,
                TTEvent.ROSTER_UPDATED,
                TTEvent.ROSTER_CREATED
        };
    
        ...
    
        private void loadRosterAndRegisterForEvents() {
            TT.getInstance().getRosterManager().getInboxEntries("organizationId", new GenericRosterListener() {
                @Override
                public void onResults(List<RosterEntry> list) {
                    //Populate recycler view roster elements...
                  
                    //Start listening for roster related events
                    TTPubSub.getInstance().addListeners(RosterActivity.this, rosterListeners);
                }
            });
        }
    
        //This method will be called asynchronously every time the SDK fires an event related to the actions you registered on your pubsub...
        @Override
        public void onEventReceived(String event, Object data) {
            switch (event) {
                case TTEvent.ROSTER_REMOVED: {
                    final RosterEntry rosterEntry = (RosterEntry) data;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //Do something with the removed roster entry (delete it from the list?)
                        }
                    });
                }
                break;
                case TTEvent.ROSTER_CREATED: {
                    final RosterEntry rosterEntry = (RosterEntry) data;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //Do something with the newly created roster entry (add it to the list?)
                        }
                    });
                }
                break;
                    ...//More events...
            }
        }
    
        //When your activity is destroyed don't forget to stop listening for events...
        @Override
        protected void onDestroy() {
            super.onDestroy();
            TTPubSub.getInstance().removeListeners(RosterActivity.this, rosterListeners);
        }
    }

4.- Loading the messages in a conversation is very similar to the example above, the only difference is that you would be using the ConversationManager to get the messages (paginated) and the events you will have to subscribe to will be message related events, like "New Message", "Message Recalled", "Message Failed", etc, The list of events fired by the SDK can be found in the TTEvent class.

## Sending a Message

There are several ways to send a message. The following snippet is one way to send a message. 

    public void sendMessage() {
            Message messageForSend =  Message.messageForSend("Hello World!", 0, 
                    rosterEntry, organizationTTL, null, null, deleteOnRead);
            TT.getInstance().getConversationManager().sendMessage(messageForSend);
            
    }

Note: RosterEntry is the person/group you are trying to send the message to, TTL is the time-to-live for the message, usually this time is specified by an organization setting.

## Sending an Attachment

Sending an attachment is very similar to sending a normal message, the difference is that a File object is required along with a mimeType of the attachment that you want to send.

    public void sendAttachment() {
            Message attachmentForSend =  Message.messageForSend("Hello World!", 0,
                    rosterEntry, organizationTTL, photoFile, AttachmentMimeTypes.TYPE_JPG, deleteOnRead);
      
    TT.getInstance().getConversationManager().sendAttachment(attachmentForSend, shouldDeleteAttachment);
    }

Sending a message/attachments is as simple as this, after your message is sent, you will start receiving message related events from the Pubsub pipeline, so, make sure you register for events like Message Delivered, Message Read etc.
