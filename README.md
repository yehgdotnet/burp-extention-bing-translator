Burp Extension - Bing Translator
================================

Testing non-English web apps is pretty straight forward which you can just use browser extension to translate what you see on screens. 
But that's not so straightforward for non-English mobile apps especially where no English support is available; in this case, pentesters have no choice but to translate each foreign keywords manually using browser navigating to translator web site. 

As of now,it processes only JSON message which is the main common format of today that mobile apps use to communicate with the app server.  For non-JSON content type, you can still use Translator tab interface.

Note that, extension data was not saved part of Burp State/project file as this feature is not natively supported by Burp yet.
Thus, your currently chosen language setting is saved on disk and restored from each Burp start-up.

For testing, use - https://yehg.net/lab/pr0js/misc/sample-json-data-multi-language.php

As this plugin is very quick and dirty built based on JSONBeautify extension, I'm sure there will be lots of bugs and improvements to be done in the future. 


Enjoy
Myo Soe
https://yehg.net/


### Building
To build a jar file:
```sh
gradle fatJar
```
