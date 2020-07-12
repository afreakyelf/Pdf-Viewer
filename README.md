<h1 align="center">Pdf Viewer</h1>

<p align="center">
A Simple PDF Viewer library which only occupies around <b>125kb</b> while most of the Pdf viewer occupies upto <b>16MB</b> space.
  
<br></br>
<br></br>
<img src="https://raw.githubusercontent.com/afreakyelf/Pdf-Viewer/master/Screenshot_2020-07-11-23-59-31-606_com.rajat.pdfviewer.jpg" width="420" height="840" />
</p>

[![](https://jitpack.io/v/afreakyelf/Pdf-Viewer.svg)](https://jitpack.io/#afreakyelf/Pdf-Viewer)

## How to integrate into your app?
Integrating the project is simple a refined all you need to do is follow the below steps

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
Okay seems like you integrated the library in your project but **how do you use it**? Well its really easy just launch the intent with in following way:

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

That's pretty much it and your all wrapped up.

## Author
Maintained by [Rajat Mittal](https://www.github.com/afreakyelf)
