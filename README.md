# EspressoXMLTestRunner
----------

# Introduction
----------
This testrunner is refer [this](https://gist.github.com/jam1401/8202320), and modified xml report srtucture that it match [Android JUnit Report Test Runner's](https://github.com/jsankey/android-junit-report) structure.

It can support Esrpesso 1.1 and 2.0.


# Quick Start
----------
1. Download jar file, and  add jar file to your android test project.
2. Edit AndroidManifest.xml to set android:name in the instrumentation tag to: **com.google.android.apps.common.testing.testrunner.EspressoXMLInstrumentationTestRunner**
3. Run your test project.
4. After test finished, you can using **adb pull** command to get xml report, the xml report's path is :

		For Android 4.4, 5.0:  /sdcard/Android/data/<main_app_package>/files/junit-report.xml
		For Android 2.3 ~ 4.3: /data/data/<main_app_package>/files/junit-report.xml

# Feedback
----------

If you have any question, please contact me at: 

<weiwei19910415@gmail.com>