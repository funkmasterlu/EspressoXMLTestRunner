package com.google.android.apps.common.testing.testrunner;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.test.AndroidTestRunner;
import android.test.InstrumentationTestRunner;
import android.test.TestSuiteProvider;
import android.util.Log;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;
import junit.framework.TestSuite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import com.google.android.apps.common.testing.testrunner.GoogleInstrumentation;
import com.google.android.apps.common.testing.testrunner.UsageTracker;
import com.google.android.apps.common.testing.testrunner.UsageTrackerRegistry;


import android.support.test.runner;


public class EspressoXMLInstrumentationTestRunner extends GoogleInstrumentation
		implements TestSuiteProvider {
	private static final long MILLIS_TO_WAIT_FOR_ACTIVITY_TO_STOP = TimeUnit.SECONDS
			.toMillis(2);
	private static final String LOG_TAG = "GoogleInstrTest";
	private BridgeTestRunner bridgeTestRunner = new BridgeTestRunner();
	private Writer mWriter;
	private XmlSerializer mTestSuiteSerializer;
	private long mTestStarted;
	private String mOutFileName = null;
	private static final String OUT_FILE_ARG = "outfile";
	private static final String OUT_FILE_DEFAULT = "junit-report.xml";

	@Override
	public void finish(int resultCode, Bundle results) {

		try {
			UsageTrackerRegistry.getInstance().sendUsages();
		} catch (RuntimeException re) {
			Log.w(LOG_TAG, "Failed to send analytics.", re);
		}
		super.finish(resultCode, results);
	}

	@Override
	public void onCreate(Bundle arguments) {
		if (arguments != null) {
			mOutFileName = arguments.getString(OUT_FILE_ARG);
		}

		if (mOutFileName == null) {
			mOutFileName = OUT_FILE_DEFAULT;
		}
		super.onCreate(arguments);
		mockitoWorkarounds();

		String disableAnalyticsStringValue = arguments
				.getString("disableAnalytics");
		boolean disableAnalytics = Boolean
				.parseBoolean(disableAnalyticsStringValue);

		if (!disableAnalytics) {
			UsageTracker tracker = new AnalyticsBasedUsageTracker.Builder(
					getTargetContext()).buildIfPossible();

			if (null != tracker) {
				UsageTrackerRegistry.registerInstance(tracker);
			}
		}

		Log.i(LOG_TAG, "Test Started!");

		// bridge will call start()
		bridgeTestRunner.onCreate(arguments);
	}

	@Override
	public TestSuite getTestSuite() {
		return bridgeTestRunner.getTestSuite();
	}

	@Override
	public void start() {
		List<TestCase> testCases = bridgeTestRunner.getAndroidTestRunner()
				.getTestCases();

		// Register a listener to update the current test description.
		bridgeTestRunner.getAndroidTestRunner().addTestListener(
				new TestListener() {
					@Override
					public void startTest(Test test) {
						runOnMainSync(new ActivityFinisher());
					}

					@Override
					public void endTest(Test test) {
					}

					@Override
					public void addFailure(Test test, AssertionFailedError ae) {
					}

					@Override
					public void addError(Test test, Throwable t) {
					}
				});
		super.start();
	}

	private void startEspressoOutput(FileWriter fileWriter) {
		Log.i(LOG_TAG,
				"###################### Starting Espresso Output ###################################");
		mWriter = fileWriter;
		mTestSuiteSerializer = newSerializer(mWriter);
		try {
			mTestSuiteSerializer.startDocument(null, null);
			mTestSuiteSerializer.startTag(null, "testsuites");
			mTestSuiteSerializer.startTag(null, "testsuite");		
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private XmlSerializer newSerializer(Writer writer) {
		Log.i(LOG_TAG,
				"###################### Getting New Serializer ###################################");
		try {
			XmlPullParserFactory pf = XmlPullParserFactory.newInstance();
			XmlSerializer serializer = pf.newSerializer();
			serializer.setOutput(writer);
			return serializer;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onStart() {
		File dir = getTargetContext().getExternalFilesDir(null);
		if (dir == null) {
			dir = getTargetContext().getFilesDir();
		}

		final File outFile = new File(dir, mOutFileName);
		try {
			startEspressoOutput(new FileWriter(outFile));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		// let the parent bring the app to a sane state.
		super.onStart();
		UsageTrackerRegistry.getInstance().trackUsage("TestRunner");

		try {
			// actually run tests!
			bridgeTestRunner.onStart();
		} finally {
		}
	}

	private void mockitoWorkarounds() {
		workaroundForMockitoOnEclair();
		specifyDexMakerCacheProperty2();
	}

	
	private void specifyDexMakerCacheProperty2() {
		// DexMaker uses heuristics to figure out where to store its temporary
		// dex files
		// these heuristics may break (eg - they no longer work on JB MR2). So
		// we create
		// our own cache dir to be used if the app doesnt specify a cache dir,
		// rather then
		// relying on heuristics.
		//

		File dexCache = getTargetContext().getDir("dxmaker_cache",
				Context.MODE_PRIVATE);
		System.getProperties().put("dexmaker.dexcache",
				dexCache.getAbsolutePath());
	}
	
	/**
	 * Enables the use of Mockito on Eclair (and below?).
	 */
	private static void workaroundForMockitoOnEclair() {
		// This is a workaround for Eclair for
		// http://code.google.com/p/mockito/issues/detail?id=354.
		// Mockito loads the Android-specific MockMaker (provided by DexMaker)
		// using the current
		// thread's context ClassLoader. On Eclair this ClassLoader is set to
		// the system ClassLoader
		// which doesn't know anything about this app (which includes DexMaker).
		// The workaround is to
		// use the app's ClassLoader.
		// TODO(user): Remove this workaround once Eclair is no longer
		// supported.

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ECLAIR_MR1) {
			return;
		}

		// Make Mockito look up a MockMaker using the app's ClassLoader, by
		// asking Mockito to create
		// a mock of an interface (java.lang.Runnable).
		ClassLoader originalContextClassLoader = Thread.currentThread()
				.getContextClassLoader();
		Thread.currentThread().setContextClassLoader(
				EspressoXMLInstrumentationTestRunner.class.getClassLoader());

		// Since we don't require users of this class to use Mockito, we can
		// only invoke Mockito via
		// the Reflection API.
		try {
			Class mockitoClass = Class.forName("org.mockito.Mockito");
			try {
				// Invoke org.mockito.Mockito.mock(Runnable.class)
				mockitoClass.getMethod("mock", Class.class).invoke(null,
						Runnable.class);
			} catch (Exception e) {
				throw new RuntimeException(
						"Workaround for Mockito on Eclair and below failed", e);
			}
		} catch (ClassNotFoundException ignored) {
			// Mockito not present -- no need to do anything
		} finally {
			Thread.currentThread().setContextClassLoader(
					originalContextClassLoader);
		}
	}

	/**
	 * Bridge that allows us to use the argument processing / awareness of stock
	 * InstrumentationTestRunner along side the seperate inheritance hierarchy
	 * of GoogleInstrumentation(and TestRunner).
	 * 
	 * This is regrettable but android's ITR is not very extension friendly. You
	 * may have to add additional method bridging in the future.
	 */
	private class BridgeTestRunner extends InstrumentationTestRunner {
		private AndroidTestRunner myAndroidTestRunner = new AndroidTestRunner() {
			@Override
			public void setInstrumentation(Instrumentation instr) {
				super.setInstrumentation(EspressoXMLInstrumentationTestRunner.this);
			}

			@Override
			public void setInstrumentaiton(Instrumentation instr) {
				super.setInstrumentation(EspressoXMLInstrumentationTestRunner.this);
			}

		};

		@Override
		public Context getTargetContext() {
			return EspressoXMLInstrumentationTestRunner.this.getTargetContext();
		}

		@Override
		public Context getContext() {
			return EspressoXMLInstrumentationTestRunner.this.getContext();
		}

		@Override
		public void start() {
			EspressoXMLInstrumentationTestRunner.this.start();
		}

		@Override
		public AndroidTestRunner getAndroidTestRunner() {
			return myAndroidTestRunner;
		}

		@Override
		public void sendStatus(int resultCode, Bundle results) {
			Log.i(LOG_TAG, "###################### SendStatus " + resultCode
					+ " ###################################");
			
			switch (resultCode) {
			case REPORT_VALUE_RESULT_ERROR:
			case REPORT_VALUE_RESULT_FAILURE:
			case REPORT_VALUE_RESULT_OK:
				try {
					recordTestResult(resultCode, results);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				break;
			case REPORT_VALUE_RESULT_START:
				recordTestStart(results);
				break;
			default:
				break;
			}
			EspressoXMLInstrumentationTestRunner.this.sendStatus(resultCode,
					results);
		}

		private void recordTestStart(Bundle results) {
			mTestStarted = System.currentTimeMillis();
			//Add attribute to testsuite tag 2014.9.18
			try {
				mTestSuiteSerializer.attribute(null, "name", results.getString(REPORT_KEY_NAME_CLASS));
			} catch (IllegalArgumentException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IllegalStateException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		private void recordTestResult(int resultCode, Bundle results)
				throws IOException {
			float time = (System.currentTimeMillis() - mTestStarted) / 1000.0f;
			String className = results.getString(REPORT_KEY_NAME_CLASS);
			String testMethod = results.getString(REPORT_KEY_NAME_TEST);
			String stack = results.getString(REPORT_KEY_STACK);
			int current = results.getInt(REPORT_KEY_NUM_CURRENT);
			int total = results.getInt(REPORT_KEY_NUM_TOTAL);

			Log.i(LOG_TAG,
					"###################### RecordTest Result ###################################");
			Log.i(LOG_TAG, "##### classname = " + className + " testmethod = "
					+ testMethod);
			
			mTestSuiteSerializer.startTag(null, "testcase");
			mTestSuiteSerializer.attribute(null, "classname", className);
			mTestSuiteSerializer.attribute(null, "name", testMethod);

			if (resultCode != REPORT_VALUE_RESULT_OK) {
				mTestSuiteSerializer.startTag(null, "failure");
				if (stack != null) {
					String reason = stack.substring(0, stack.indexOf('\n'));
					String message = "";

					int index = reason.indexOf(':');
					if (index > -1) {
						message = reason.substring(index + 1);
						reason = reason.substring(0, index);
					}
					Log.i(LOG_TAG, "##### testfailed message = " + message
							+ " reason  = " + reason);
					mTestSuiteSerializer.attribute(null, "message", message);
					mTestSuiteSerializer.attribute(null, "type", reason);  //add type 2014.9.19
					mTestSuiteSerializer.text(stack);
				}
				mTestSuiteSerializer.endTag(null, "failure");
			} else {
				mTestSuiteSerializer.attribute(null, "time",
						String.format("%.3f", time));
				Log.i(LOG_TAG, "##### testPassed time = " + time);
			}

			mTestSuiteSerializer.endTag(null, "testcase");

			Log.i("Current:", Integer.toString(current));
			Log.i("Total:",  Integer.toString(current));
			
			if (current == total) {
				//Delete Tag 2014.9.19
				/*
				mTestSuiteSerializer.startTag(null, "system-out");
				mTestSuiteSerializer.endTag(null, "system-out");
				mTestSuiteSerializer.startTag(null, "system-err");
				mTestSuiteSerializer.endTag(null, "system-err");
				*/
				mTestSuiteSerializer.endTag(null, "testsuite");
				mTestSuiteSerializer.flush();
			}

		}

		@Override
		public void finish(int resultCode, Bundle results) {
			Log.i(LOG_TAG,
					"###################### finish ###################################");

			endTestSuites();
			EspressoXMLInstrumentationTestRunner.this.finish(resultCode,
					results);
		}

		private void endTestSuites() {
			try {
				if (mTestSuiteSerializer != null) {
					mTestSuiteSerializer.endTag(null, "testsuites");
					mTestSuiteSerializer.endDocument();
					mTestSuiteSerializer.flush();
				}

				if (mWriter != null) {
					mWriter.flush();
					mWriter.close();
				}
				Log.i(LOG_TAG,
						"###################### flushed and delivered ###################################");
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}