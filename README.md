<h1 align="center">Pdf Viewer For Android</h1>

<p align="center">
A Simple PDF Viewer library which only occupies around <b>125kb</b> while most of the Pdf viewer occupies upto <b>16MB</b> space.
<br>
<br>
<img src="https://raw.githubusercontent.com/afreakyelf/Pdf-Viewer/master/Screenshot_2020-07-11-23-59-31-606_com.rajat.pdfviewer.jpg" width="420" height="840" />
</p>

[![](https://jitpack.io/v/afreakyelf/Pdf-Viewer.svg)](https://jitpack.io/#afreakyelf/Pdf-Viewer) [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/Apache-2.0) ![](https://img.shields.io/github/forks/afreakyelf/Pdf-Viewer?label=Forks)
![](https://img.shields.io/github/stars/afreakyelf/Pdf-Viewer?label=Stars&color=9cf)


## How to integrate into your app?
Integrating the project is simple, All you need to do is follow the below steps

Step 1. Add the JitPack repository to your build file. Add it in your root build.gradle at the end of repositories:

```java
allprojects {
  repositories {
    ...
    maven { url "https://jitpack.io" }
  }
}
```
Step 2. Add the dependency
```java
dependencies {
    implementation 'com.github.afreakyelf:Pdf-Viewer:Tag'
}
```

## How to use the library?
Okay seems like you have integrated the library in your project but **how do you use it**? Well its really easy just launch the intent with in following way:

```kotlin
open_pdf.setOnClickListener {
            startActivity(
                PdfViewerActivity.buildIntent(
                    context,                                                                      
                    "pdf_url",                                // PDF URL in String format
                    false,
                    "Pdf title/name ",                        // PDF Name/Title in String format
                    "pdf directory to save",                  // If nothing specific, Put "" it will save to Downloads
                    enableDownload = false                    // This param is true by defualt.
                )
            )
        }
```

That's pretty much it and you're all wrapped up.


## Donations
If this project help you reduce time to develop, you can give me a cup of coffee :) 

[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/paypalme/afreakyelf)

## Author
Maintained by [Rajat Mittal](https://www.github.com/afreakyelf)
