
# Quick Start Guide
The Android SDK is a quick way to integrate secure messaging within a native Android app. This quick start guide will help you get the SDK added to your project.

We are working to put together a
Android demo app but in the meantime, if you have any questions, don't
hesitate to [Contact Us](mailto:developersupport@tigertext.comSubject=Android%20SDK%20Help) 
Contact TigerText to Check out the SDK.

## Installation
To setup and initiate the Android SDK, follow the following setup steps.  First, download the latest version TigerConnect Android SDK. The SDK itself is not open source, contact TigerText to get access to the SDK

In your project's base directory, create a folder called `ttandroid` and place the `ttandroid.aar` inside. Then add a `build.gradle` file with:

    configurations.create("default")
    artifacts.add("default", file('ttandroid-3.0.1.aar'))


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
At your project's level build.gradle, add the JitPack repository.
 
        allprojects {
		repositories {
		    ...
		    maven { url 'https://jitpack.io' }
		}
    }


Until we publish to an artifact repository soon, you will need to manually add the dependencies of the ttandroid.aar since AAR's don't (by default) come with their dependencies bundled.

Open your app's build.gradle (YOUR_APP/build.gradle in this case) and add these lines, this includes the instruction for compiling the 'ttandroid.aar' as well as the items for multidexing (as it will be necessary).  You will also need some flavor of the support v4 library.

    android {
        ...
    
        defaultConfig {
            ...
            multiDexEnabled true
        }
        dexOptions {
            javaMaxHeapSize "4g"
        }
    }
    
    	dependencies {
	    ...
	    implementation fileTree(dir: 'libs', include: ['*.jar'])
	    implementation project(':ttandroid')
	    implementation 'com.github.heremaps:oksse:master-SNAPSHOT'
	    def boltsVersion = '1.4.0'
	    implementation "com.parse.bolts:bolts-tasks:$boltsVersion"
	    implementation "com.parse.bolts:bolts-applinks:$boltsVersion"
	    implementation "com.google.firebase:firebase-messaging:17.5.0"
	    implementation 'com.google.firebase:firebase-core:16.0.8'
	    implementation 'net.zetetic:android-database-sqlcipher:3.5.9'
	    implementation 'com.squareup.retrofit2:retrofit:2.5.0'
	    implementation 'com.jakewharton.timber:timber:4.7.1'
	    def stethoVersion = '1.5.0'
	    implementation "com.facebook.stetho:stetho:$stethoVersion"
	    implementation "com.facebook.stetho:stetho-okhttp3:$stethoVersion"
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

Getting realtime pull / push going is pretty straightforward. Simply add TT.getInstance().startService() and TT.getInstance().stopService(); calls at the times that your application comes to the foreground (after a user is authenticated of course) and when the application goes into the background(if necessary). One way to achieve this is via [Lifecycle Observer](https://developer.android.com/reference/android/arch/lifecycle/LifecycleObserver) registered within your Application class, for example:

    public class RealTimeEventsTracker implements LifecycleObserver {

    public RealTimeEventsTracker() {
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onForeground() {
        isForegrounded = true;
        TT.getInstance().getAccountManager().setPresenceStatus(true);

        if (!TT.getInstance().getAccountManager().isLoggedIn()) return;

        // If the sse manager is already connected, set the online presence to available.
        if (TTService.isSSEConnected()) {
            TT.getInstance().getAccountManager().setOnlinePresence(true);
        }

        startRealTimeEventsService();
        bindService();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onBackground() {
        // Changing the online presence to be away since the app is now backgrounded.
        TT.getInstance().getAccountManager().setPresenceStatus(false);

        if (!TT.getInstance().getAccountManager().isLoggedIn()) return;

        unbindService();

        // If the sse manager is already connected, set the online presence to away.
        if (TTService.isSSEConnected()) {
            TT.getInstance().getAccountManager().setOnlinePresence(false);
        }

        stopRealTimeEventsService();
    }

    private void startRealTimeEventsService() {
        try {
            TT.getInstance().startService();
        } catch (IllegalStateException e) {
            String errorMessage = "startRealTimeEventsService: Error starting service";
            Timber.e(e, errorMessage);
        }
    }

    private void stopRealTimeEventsService() {
        TT.getInstance().stopService();
    }

Then, in your Application class's onCreate method, register the RealTimeEventsTracker observer.

	RealTimeEventsTracker realTimeEventsTracker = new RealTimeEventsTracker();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(realTimeEventsTracker);

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
        private static final String[] rosterListeners = new String[]{
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
        private static final String[] rosterListeners = new String[]{
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

4.- Loading the messages in a conversation is very similar to the example above, the only difference is that you would be using the ConversationManager to get the messages (paginated) and the events you will have to subscribe to will be message related events, like "Message Added", "Messages Removed", "Message Failed", etc, The list of events fired by the SDK can be found in the TTEvent class.

	TT.getInstance().getConversationManager().getMessagesByPage(rosterEntry, pageSize, topMessage, new GenericActionListener<List<Message>, Throwable>() {
            @Override
            public void onResult(List<Message> messages) {
                // Post the messages in Live Data
                messagesLiveData.postValue(messages);
            }

            @Override
            public void onFail(Throwable throwable) {
                Timber.e(throwable, "Failed getting messagesLiveData by page");
            }
        });

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

## Resending a Message

	private void resendMessage(Message message) {
		// Notify our Conversation Manager that this message needs to be resent
		TT.getInstance().getConversationManager().resendMessage(message.getMessageId());
		conversationAdapter.addMessage(message);
		scrollToBottom();
    }
	
## Recalling a Message

	private void recallMessage(Message message) {
		// Notify our Conversation Manager that this message needs to be recalled
		TT.getInstance().getConversationManager().recallMessage(message.getMessageId());
    }

## Forwarding a Message

	private void forwardMessage(Message message) {
		// Notify our Conversation Manager that this message is to be forwarded
		TT.getInstance().getConversationManager().forwardMessage(message.getMessageId(), message);
    }

## Receiving Updates for Messages
In your Conversation View, you would want to subscribe to Message updates like `MESSAGE_ADDED`, `MESSAGE_UPDATED`, `MESSAGES_REMOVED`, etc.

	@Override
	    public void onEventReceived(@NonNull String event, @Nullable Object o) {
		if (getActivity() == null) {
		    return;
		}

		switch (event) {
		    case TTEvent.MESSAGE_ADDED: {
			/**
			 *  It may also be useful to check if the fragment is alive as well!
			 */
			// Confirm that this new message is for this conversation and organization,
			// if not, return early
			if (!isEventForRosterEntryOrg(o)) return;

			Timber.d("Message Added");
			Message message = (Message) o;

			/**
			 onEventReceived is done on a background thread, so make sure to publish your logic
			 on the UI thread
			 */
			getActivity().runOnUiThread(() -> {
			    if (getUserVisibleHint()) {
				// This is how you mark a conversation as read
				TT.getInstance().getConversationManager().markConversationAsRead(mRosterEntry);
			    }
			    conversationAdapter.addMessage(message);
			    scrollToBottom();
			});
		    }
		    break;
		    case TTEvent.MESSAGE_UPDATED: {
			if (!isEventForRosterEntryOrg(o)) return;
			Timber.d("Message Updated");
		    }
		    break;
		    case TTEvent.MESSAGES_REMOVED: {
			Timber.d("Messages Removed");
			List<Message> messages = (List<Message>) o;
			if (getActivity() == null) return;

			getActivity().runOnUiThread(() -> {
			    conversationAdapter.removeMessages(messages);
			});
		    }
		    break;
		    case TTEvent.MESSAGE_STATUS_RECEIVED: {
		    	// This is where to handle message status updates like Read, Delivered
			Timber.d("Message Status Received!");
			Bundle b = (Bundle) o;
			if (!isEventForRosterEntryOrg(b)) return;

			String statusString = b.getString(com.tigertext.ttandroid.constant.TTConstants.STATUS);
			Message.Status status = null;
			if (statusString != null) {
			    status = Message.Status.valueOf(statusString.toUpperCase());
			}
			if (status == Message.Status.DELIVERED && mRosterEntry.isGroup()) {
			    /***
			     * If status is Delivered and its a group, do nothing
			     */
			    return;
			}

			final Message m = b.getParcelable(com.tigertext.ttandroid.constant.TTConstants.MESSAGE);
			if (m != null && mRosterEntry.getId().equals(m.getRosterId())) {
			    getActivity().runOnUiThread(() -> {
				conversationAdapter.markMessageAsRead(m);
			    });
			}
		    }
		    break;
		}
	    }


Sending a message/attachments is as simple as this, after your message is sent, you will start receiving message related events from the Pubsub pipeline, so, make sure you register for `MESSAGE_STATUS_RECEIVED` to let users know that people have read their conversation. 

## Creating a Group
	private void createGroup(String groupName, List<String> users) {
        // Call this method to notify our Roster Manager to create a group in our server
        TT.getInstance().getRosterManager().createGroup(users, organizationID, groupName, null);
    }

## Creating a Forum
	private void createForum(String forumName, String forumDescription) {
		// Call this method to notify our Roster Manager to create a forum in our server
		TT.getInstance().getRosterManager().createRoom(forumName, organizationID, null, forumDescription, null);
	}

If you need more information about our SDK you can check the [JavaDocs](http://tigertext.github.io/android_sdk_demo/javadoc/)
