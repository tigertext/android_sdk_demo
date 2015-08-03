# TigerText Android SDK

#SETUP

1. Create an Application class in your project.
2. Instantiate TT.java in Application class’s onCreate() Method.

#TT SDK MANAGERS

There are individual manager classes provided by TT api. All the managers are singleton and each object can be retrieved by TT.getInstance().getManagerName().

1. TigertextAccountManager : This is used to login and logout calls. It provides appropriate callbacks for login and logout success and failure.

2. OrganizationManager : Refer class doc

3. RosterManager : This is used for all the operations related to roster.

4. ConversationManager (New) : This is a new class which acts like a manager for all the message related stuff. Some methods are still part of “Conversation” class but gradually this class will be deprecated. 

5. SearchManager : This manager is used to search for people in the organization

6. TTPubsub : This is a broadcast manager which runs on a background thread. At this moment it broadcast just ROSTER_CREATED event. More events will be added to it in the upcoming releases. Refer the class docs for usage.

#USAGE

1. Instantiate TT.java in Application.java class.
     public void onCreate(){
     	……
     	TT.init();
     }
2. Login to the system using AccountManager.
	AccountManager am = TT.getInstance().getAccountManager();
	am.login()
3. Once loggedIn successfully , call TT.sync() to get all the data from the server.
4. Once successfully synced the data (roster, messages etc), register for GCM/SSE and register the appropriate loaders.

#NOTE
There is a ConversationManager which is a singleton class to provide operations related to messages like sending message , retrieving attachments etc. There is an old class conversation which needs to be instantiated for every roster to handle message related functions. Conversation class will be deprecated soon. In case 2 methods are found in both the classes, prefer Conversationmanager.

