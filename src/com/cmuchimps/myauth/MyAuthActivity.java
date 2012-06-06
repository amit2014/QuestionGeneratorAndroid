package com.cmuchimps.myauth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.cmuchimps.myauth.KnowledgeTranslatorWrapper.KnowledgeSubscription;
import com.cmuchimps.myauth.QuestionGenerator.QuestionAnswerPair;

public class MyAuthActivity extends Activity {
    /** Called when the activity is first created. */
	private KBXMLParser mParser;
	private KBDbAdapter mDbHelper;
	private DataWrapper dw;
	private QuestionGenerator qg;
	private KnowledgeTranslatorWrapper ktw;
	private int pollAllMenuId;
	
	public static final Random r = new Random();
	private QuestionAnswerPair prevQ;
	private QuestionAnswerPair currQ;
	
	private LocationManager lm;
	private LocationListener ll;
	private ConnectivityManager cm;
	
	private User mUser;
	private ServerCommunicator mCommunicator;
	
	//form fields
	TextView question_prompt;
	EditText input;
	Button submit;
	Button skip;
	RadioGroup radioSupp1;
	RadioGroup radioSupp2;
	RadioGroup radioSupp3;
	
	//state fields
	private int mSuppResponse1;
	private int mSuppResponse2;
	private int mSuppResponse3;
	//state fields for skipping
	private boolean[] mChoice;
	
	private final int NUM_SKIP_CHOICES = 4;
	
	private final int DIALOG_CONTACT = 0;
	private final int DIALOG_INST = 1;
	private final int DIALOG_SUB_ERROR = 2;
	private final int DIALOG_SKIP_PICKER = 3;
	
	private final int CONSENT_RESULT = 0;
	private final int NEW_USER_RESULT = 1;
	
	private boolean mConsentRequested;
	private final String INSTRUCTIONS = 
			"Thank you for participating in this study!\n\n" +
			"Detailed instructions can be found at http://casa.cmuchimps.org/instructions.html\n\n" +
			"In short, please answer as many questions as you can everyday.\n\n" +
			"Don't forget to hit Send Packets to Server (in the menu options) when you are finished!";
	
	//private final int seed;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.question);
        
        /* Initialize UI elements */
        question_prompt = (TextView)this.findViewById(R.id.question_prompt);
        submit = (Button)this.findViewById(R.id.question_submit);
        skip = (Button)this.findViewById(R.id.question_skip);
        radioSupp1 = (RadioGroup)this.findViewById(R.id.radioSupp1);
        radioSupp2 = (RadioGroup)this.findViewById(R.id.radioSupp2);
        radioSupp3 = (RadioGroup)this.findViewById(R.id.radioSupp3);
        input = (EditText)this.findViewById(R.id.user_answer);
        
        /* Make UI elements pretty */
        submit.getBackground().setColorFilter(0xFF00FF00, PorterDuff.Mode.MULTIPLY);
        skip.getBackground().setColorFilter(0xFFFF0000, PorterDuff.Mode.MULTIPLY);
        
        /* Initialize UI element event listeners */
        skip.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (currQ != null) {
					prevQ = currQ;
					resetFields();
					askQuestion();
					showDialog(DIALOG_SKIP_PICKER);
				} else { //because ordering matters above
					resetFields();
					askQuestion();
				}
			}
        });
        
        submit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (input.getText().toString().length() > 0 && radioSupp1.getCheckedRadioButtonId() >= 0 && radioSupp2.getCheckedRadioButtonId() >= 0 && radioSupp3.getCheckedRadioButtonId() >= 0) {
					/**
					 * Validate that all appropriate parts of the form are filled in.
					 * Create TransmissionPacket with the answer;
					 * Ask new question and reset fields
					 */
					if (currQ != null) {
						String user_id = getUser().unique_id;
						String qtext = currQ.getQuestion();
						HashMap<String,String> question = currQ.getQuestionMetas();
						ArrayList<HashMap<String,String>> answer_metas = currQ.getAnswerMetas();
						String user_answer = input.getText().toString();
						HashMap<String,String> supplementary_responses = new HashMap<String,String>();
						supplementary_responses.put("supp1",""+mSuppResponse1);
						supplementary_responses.put("supp2",""+mSuppResponse2);
						supplementary_responses.put("supp3",""+mSuppResponse3);
						String timestamp = (String) DateFormat.format("yyyy-MM-dd kk:mm:ss", System.currentTimeMillis());
						try {
							mCommunicator.queuePacket(new TransmissionPacket(ServerCommunicator.getNextPacketID(getFilesDir()),user_id,qtext,question,answer_metas,user_answer,supplementary_responses,timestamp));
						} catch (IOException e) {
							e.printStackTrace();
							Toast.makeText(getApplicationContext(), "An error occured. The packet could not be saved...", Toast.LENGTH_SHORT).show();
						}	
					}
					resetFields();
					askQuestion();
				} else {
					showDialog(DIALOG_SUB_ERROR);
				}
			}
        });
        
        radioSupp1.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (checkedId >= 0) {
					mSuppResponse1 = Integer.parseInt(((RadioButton)findViewById(checkedId)).getText().toString());
				}
			}
        });

        radioSupp2.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (checkedId >= 0) {
					mSuppResponse2 = Integer.parseInt(((RadioButton)findViewById(checkedId)).getText().toString());
				}
			}
        });
        
        radioSupp3.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (checkedId >= 0) {
					mSuppResponse3 = Integer.parseInt(((RadioButton)findViewById(checkedId)).getText().toString());
				}
			}
        });
        
        /* Initialize members */
        this.mCommunicator = new ServerCommunicator(this.getApplicationContext());
        this.mConsentRequested = false;
        this.mDbHelper = new KBDbAdapter(this);
        this.mDbHelper.open();
        this.dw = new DataWrapper(mDbHelper);
        this.qg = new QuestionGenerator(mDbHelper,dw,getFilesDir());
        this.lm = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        this.ktw = new KnowledgeTranslatorWrapper();
        this.mChoice = new boolean[NUM_SKIP_CHOICES];
    	for (int i = 0; i < mChoice.length; i++) {
    		this.mChoice[i] = false;
    	}
    	
        /* Initialize managers and sensor listeners */
        this.initializeLocationListener();
        cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
    }
    
    private User getUser() {
    	if (mUser != null) return mUser;
    	else {
    		try {
    			mUser = User.load(getFilesDir());
    		} catch (Exception e) {
    			e.printStackTrace();
    			System.out.println("Could not load user");
    		}
    		return mUser;
    	}
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	/* Populate database with scaffolds (qats and subscriptions) if needed */
    	if (mDbHelper.getNumSubscriptions() == 0) { //if we have no knowledge subscriptions
    		this.repopulateDB();
    		this.forcePollSubscriptions();
    	}
    	/* Control flow: consent form if not accepted => New User Activity if no user => Main Q/A activity */
    	if (!(new File(getFilesDir(),ConsentFormActivity.CONSENT_FILE)).exists()) {
    		if (!mConsentRequested) {
    			startActivityForResult(new Intent(this, ConsentFormActivity.class), CONSENT_RESULT);
    			mConsentRequested = true;
    		}
    	} else if (mUser == null && !User.exists(getFilesDir())) {
    		startActivityForResult(new Intent(this, NewUserActivity.class), NEW_USER_RESULT);
    	} else {
    		try {
				mUser = User.load(getFilesDir());
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    	
    	/* Set alarms for updating knowledge subscriptions and uploading packets to server */
    	this.setUpdaterAlarm();
    	this.setUploaderAlarm();
    	
    	/* Initialize location manager */
    	if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10*UtilityFuncs.MIN_TO_MILLIS, 0, ll);
		} else if (lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10*UtilityFuncs.MIN_TO_MILLIS, 0, ll);
		}
    }
    
    @Override
    public void onStart() {
    	super.onStart();
    	/* Populate database with scaffolds (qats and subscriptions) if needed */
        if (mDbHelper.getNumSubscriptions() == 0) { //if we have no knowledge subscriptions
    		this.repopulateDB();
    		this.forcePollSubscriptions();
    	}
        
        /* Control flow: consent form if not accepted => New User Activity if no user => Main Q/A activity */
    	if (!(new File(getFilesDir(),ConsentFormActivity.CONSENT_FILE)).exists()) {
    		this.forcePollSubscriptions();
    		if (!mConsentRequested) {
    			startActivityForResult(new Intent(this, ConsentFormActivity.class),CONSENT_RESULT);
    			mConsentRequested = true;
    		}
    	} else if (mUser == null && !User.exists(getFilesDir())) {
    		startActivityForResult(new Intent(this, NewUserActivity.class),NEW_USER_RESULT);
    	} else {
    		try {
				mUser = User.load(getFilesDir());
				System.out.println("User:");
				System.out.println(mUser);
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    	
    	/* Set alarms for updating knowledge subscriptions and uploading packets to server */
    	this.setUpdaterAlarm();
    	this.setUploaderAlarm();
    	
    	/* Initialize location manager */
		if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10*UtilityFuncs.MIN_TO_MILLIS, 0, ll);
		} else if (lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10*UtilityFuncs.MIN_TO_MILLIS, 0, ll);
		}
		askQuestion();
    }
    
    @Override
    public void onPause() {
    	if (lm != null) lm.removeUpdates(ll);
    	if ((cm != null && cm.getActiveNetworkInfo().isConnectedOrConnecting()) && mCommunicator.hasQueuedPackets()) { 
    		Intent uploader = new Intent(getApplicationContext(),UploaderService.class);
    		this.startService(uploader);
    	}
    	
    	try {
	    	ServerCommunicator.serializePacketID(getFilesDir());
	    	qg.serializeIDTrack();
    	} catch (IOException e) {
    		System.out.println("Could not serialize...");
    		e.printStackTrace();
    	}
    	
    	super.onPause();
    }
    
    @Override
    public void onStop() {
    	lm.removeUpdates(ll);
    	if (cm.getActiveNetworkInfo().isConnectedOrConnecting() && mCommunicator.hasQueuedPackets()) { 
    		Intent uploader = new Intent(getApplicationContext(),UploaderService.class);
    		this.startService(uploader);
    	}
    	
    	try {
	    	ServerCommunicator.serializePacketID(getFilesDir());
	    	qg.serializeIDTrack();
    	} catch (IOException e) {
    		System.out.println("Could not serialize...");
    		e.printStackTrace();
    	}
    	
    	super.onStop();
    }
    
    private void resetFields() {
    	input.setText("");
    	radioSupp1.clearCheck();
    	radioSupp2.clearCheck();
    	radioSupp3.clearCheck();
    	mSuppResponse1 = -1;
    	mSuppResponse2 = -2;
    	mSuppResponse3 = -3;
    }
    
    private void deleteUser() {
    	File f = new File(getFilesDir(),User.USER_FILE);
    	f.delete();
    }
    
    private void initializeLocationListener() {
		ll = new LocationListener() {

			@Override
			public void onLocationChanged(Location location) {
				// TODO Auto-generated method stub
				//create fact about location here
				//2 tags: person:User was at location:Towers=
				//also get Timestamp and day of week
				System.out.println("Location changed event has triggered for some reason.");
				if (System.currentTimeMillis() > mDbHelper.getSubscriptionDueTimeFor("Location")) {
					System.out.println("adding location fact from location listener");
					String timestamp, dayOfWeek;
					ArrayList<HashMap<String,String>> tags = new ArrayList<HashMap<String,String>>(),metas = new ArrayList<HashMap<String,String>>();
					Date date = new Date(location.getTime());
					timestamp = (String) DateFormat.format("yyyy-MM-dd kk:mm:ss", date);
					dayOfWeek = (String) DateFormat.format("EEEE", date);
					//add person:User tag
					HashMap<String,String> curr = new HashMap<String,String>();
					curr.put("tag_class", "Person");
					curr.put("subclass", "User");
					tags.add(curr);
					//add location:GPS tag
					curr = new HashMap<String,String>();
					curr.put("tag_class", "Location");
					curr.put("subclass", "Provider");
					curr.put("subvalue", location.getProvider());
					tags.add(curr);
					//add location:Geopoint tag
					curr = new HashMap<String,String>();
					curr.put("tag_class", "Location");
					curr.put("subclass", "Geopoint");
					curr.put("subvalue", location.getLatitude() + "," + location.getLongitude());
					tags.add(curr);
					//add location:Accuracy tag
					curr = new HashMap<String,String>();
					curr.put("tag_class","Location");
					curr.put("subclass", "Geopoint");
					curr.put("subvalue", ""+location.getAccuracy());
					tags.add(curr);
					//TODO: Should we do reverse geocoding here? Who knows.
					//no metas for now
					mDbHelper.createFact(timestamp, dayOfWeek, tags, metas);
					mDbHelper.updateSubscriptionTime("Location", location.getTime());
				}
			}

			@Override
			public void onProviderDisabled(String provider) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onProviderEnabled(String provider) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onStatusChanged(String provider, int status,
					Bundle extras) {
				// TODO Auto-generated method stub
				
			}
			
		};
    }
    
    @Override
    public void onDestroy() {
    	this.mDbHelper.close();
    	super.onDestroy();
    }
    
    private void askQuestion() {
    	currQ = qg.askQuestion();
        if (currQ != null) {
	        question_prompt.setText(currQ.getQuestion());
        } else {
        	Toast.makeText(getApplicationContext(), "Could not create a question...", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void printFacts(Long[] facts) {
    	System.out.println("ALL FACTS:\n");
        for (Long l : facts) {
        	System.out.println(mDbHelper.getFact(l));
        }
    }
    
    private void printQATS(Long[] qats) {
        System.out.println("ALL QATS:\n");
        for (Long l : qats) { 
        	System.out.println(mDbHelper.getQAT(l));
        }
    }
    
    private void repopulateDB() {
        try {
			this.populateTagClasses(this.getAssets().open("qg_other_files/tag_classes.txt"));
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        this.mParser = new KBXMLParser(this,mDbHelper);
        //mParser.parseFactBase("qg_xml/knowledgebase.xml");
        mParser.parseQATs("qg_xml/qats.xml");
        createSensorSubscriptions();
    }
    
    private void deleteDB() {
    	this.deleteDatabase(mDbHelper.DATABASE_NAME);
	}	
    /**
     * 
     * @param file
     */
    private void populateTagClasses(InputStream file) {
    	try {
			BufferedReader br = new BufferedReader(new InputStreamReader(file));
			String input;
			while ((input = br.readLine()) != null) {
				if (this.mDbHelper == null) System.out.println("mDbHelper is null?");
				this.mDbHelper.createTagClass(input.replace("\n", ""));
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    /**
     * Sets updater alarm so that subscriptions are polled periodically.
     */
    private void setUpdaterAlarm() {
    	Intent updater = new Intent(this, SubscriptionReceiver.class);
    	PendingIntent recurringUpdate = PendingIntent.getBroadcast(getApplicationContext(), 0, updater, PendingIntent.FLAG_CANCEL_CURRENT);
    	AlarmManager alarms = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
    	//alarms.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1 * UtilityFuncs.MIN_TO_MILLIS, 10l*UtilityFuncs.MIN_TO_MILLIS, recurringUpdate);
    	alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1 * UtilityFuncs.MIN_TO_MILLIS, 10l*UtilityFuncs.MIN_TO_MILLIS, recurringUpdate);
    }
    
    private void setUploaderAlarm() {
    	Intent uploader = new Intent(this, UploaderReceiver.class);
    	PendingIntent recurringUpload = PendingIntent.getBroadcast(getApplicationContext(), 0, uploader, PendingIntent.FLAG_UPDATE_CURRENT);
    	AlarmManager alarms = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
    	alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1 * UtilityFuncs.MIN_TO_MILLIS, AlarmManager.INTERVAL_HALF_DAY, recurringUpload);
    }
    
    /**
     * Mainly for testing purposes. Manually starts intent to update all due subscriptions.
     */
    private void pollDueSubscriptions() {
    	Intent updater = new Intent(this, KnowledgeTranslatorWrapper.class);
    	String[] dueSubs = mDbHelper.getAllDueSubscriptions().keySet().toArray(new String[mDbHelper.getAllDueSubscriptions().size()]);
    	updater.putExtra("dueSubs", dueSubs);
    	this.startService(updater);
    }
    
    /**
     * Mainly for testing purposes. Manually starts intent to update all subscriptions, regardless of whether or not they are due.
     */
    private void forcePollSubscriptions() {
    	System.out.println("Entering force poll subscriptions...");
    	Intent updater = new Intent(this, KnowledgeTranslatorWrapper.class);
    	String[] dueSubs = mDbHelper.getAllSubscriptions().keySet().toArray(new String[mDbHelper.getAllSubscriptions().size()]);
    	updater.putExtra("dueSubs", dueSubs);
    	this.startService(updater);
    }
    
    /**
     * Initializes subscriptions table.
     */
    private void createSensorSubscriptions() {
    	String[] SensorSubscriptions = new String[] { "Communication", "InternetBrowsing", "Media", "UserDictionary", "Contact", "Calendar", "ApplicationUse", "Location"};
    	Long[] PollIntervals = new Long[] { 3l*UtilityFuncs.HOUR_TO_MILLIS, 3l*UtilityFuncs.DAY_TO_MILLIS, 1l*UtilityFuncs.DAY_TO_MILLIS, 7l*UtilityFuncs.DAY_TO_MILLIS, 3l*UtilityFuncs.DAY_TO_MILLIS, 7l*UtilityFuncs.DAY_TO_MILLIS, 30l*UtilityFuncs.MIN_TO_MILLIS, 30l*UtilityFuncs.MIN_TO_MILLIS};
    	
    	for (int i = 0; i < SensorSubscriptions.length; i++) {
			if (!mDbHelper.subscriptionExists(SensorSubscriptions[i])) {
				//System.out.println(System.currentTimeMillis());
				mDbHelper.createSubscription(SensorSubscriptions[i], PollIntervals[i], System.currentTimeMillis() - (1*UtilityFuncs.DAY_TO_MILLIS), "com.cmuchimps.myauth.KnowledgeTranslatorWrapper$" + SensorSubscriptions[i] + "KnowledgeSubscription");
				try {
					Class c = Class.forName("com.cmuchimps.myauth.KnowledgeTranslatorWrapper$" + SensorSubscriptions[i] + "KnowledgeSubscription");
					if (!(Modifier.isStatic(c.getModifiers()) || Modifier.isAbstract(c.getModifiers()))) {
						KnowledgeSubscription ks = (KnowledgeSubscription) c.getDeclaredConstructor(new Class[] { KnowledgeTranslatorWrapper.class }).newInstance(new Object[] { this.ktw });
						c.getMethod("poll", null).invoke(ks, null);
						System.out.println("Successfully polled " + SensorSubscriptions[i] + "KnowledgeSubscription!");
					}
				} catch (Throwable e) {
					System.out.println("Failed to update class " + SensorSubscriptions[i]);
					e.printStackTrace();
				}
			}
		}
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        	case (R.id.sendPackets):
        		if (cm.getActiveNetworkInfo().isConnectedOrConnecting() && mCommunicator.hasQueuedPackets()) { 
            		System.out.println("Starting service to upload packets...");
        			Intent uploader = new Intent(getApplicationContext(),UploaderService.class);
            		this.startService(uploader);
            	}
        		Toast.makeText(getApplicationContext(), "Sending in background...", Toast.LENGTH_SHORT).show();
        		return true;
        	case (R.id.contact):
        		this.showDialog(DIALOG_CONTACT);
        		//printFacts(mDbHelper.getAllFacts());
        		//Toast.makeText(this, "Contact Info Selected", Toast.LENGTH_SHORT).show();
        		return true;
        	case (R.id.instructions):
        		this.showDialog(DIALOG_INST);
        		//mCommunicator.printQueue();
        		//Toast.makeText(this, "Instructions Selected", Toast.LENGTH_SHORT).show();
        		return true;
        	case (R.id.refresh):
        		this.forcePollSubscriptions();
        		return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
    	System.out.println("HI ACTIVITY RESULTS: request = " + requestCode + "; result = " + resultCode);
        if (requestCode == NEW_USER_RESULT) {
            if (resultCode == RESULT_OK) {
            	//Send user packet to server if we have connectivity
            	try {
	            	if (User.exists(getFilesDir())) {
	            		mCommunicator.queuePacket((mUser == null ? User.load(getFilesDir()) : mUser));
	            	}
            	} catch (Exception e) {
            		e.printStackTrace();
            	}
            }
        } else if (requestCode == CONSENT_RESULT) {
        	if (resultCode == RESULT_CANCELED) {
        		finish();
        	} else {
        		//queue up consent for transmission
        	}
        }
    }
    
    @Override
    protected Dialog onCreateDialog(int dialogId) {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	switch (dialogId) {
    	case DIALOG_CONTACT:
    		builder.setMessage("You may reach me through phone or email\nPhone:678-978-1547\nEmail:sauvik@cmu.edu")
    			   .setNeutralButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).setCancelable(true);
    		return builder.create();
    	case DIALOG_INST:
    		builder.setMessage(INSTRUCTIONS)
    			   .setNeutralButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).setCancelable(true);
    		return builder.create();
    	case DIALOG_SUB_ERROR:
    		builder.setMessage("Please answer all fields before hitting submit.")
			    .setNeutralButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).setCancelable(true);
    		return builder.create();
    	case DIALOG_SKIP_PICKER:
    		CharSequence[] items = { "Not comfortable answering", "Totally can't remember", "Don't understand", "Other" };
    		builder.setTitle("Pick a reason for skipping:")
    			.setMultiChoiceItems(items, new boolean[] { false, false, false, false }, new DialogInterface.OnMultiChoiceClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which, boolean isChecked) {
						mChoice[which] = isChecked;
					}
				})
				.setNeutralButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String qtext = prevQ.getQuestion();
						HashMap<String,String> question = prevQ.getQuestionMetas();
						String user_id = getUser().unique_id;
						String timestamp = (String) DateFormat.format("yyyy-MM-dd kk:mm:ss", System.currentTimeMillis());
						try {
							TransmittablePacket tp = new SkipQuestionPacket(qtext, question, mChoice[0],mChoice[1],mChoice[2],mChoice[3],"",timestamp,user_id);
							mCommunicator.queuePacket(tp);
							System.out.println(tp);
						} catch (IOException e) {
							e.printStackTrace();
							Toast.makeText(getApplicationContext(), "An error occured. The packet could not be saved...", Toast.LENGTH_SHORT).show();
						}
						dialog.dismiss();
					}
				})
				.setCancelable(false);
    		return builder.create();
    	default:
    		return null;
    	}
    }
    
}