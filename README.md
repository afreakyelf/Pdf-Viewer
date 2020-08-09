<h1 align="center">Pdf Viewer For Android</h1>

<p align="center">
A Simple PDF Viewer library which only occupies around <b>125kb</b> while most of the Pdf viewer occupies upto <b>16MB</b> space.
<br>
<br>
<img src="https://raw.githubusercontent.com/afreakyelf/Pdf-Viewer/master/Screenshot_2020-07-11-23-59-31-606_com.rajat.pdfviewer.jpg" width="420" height="840" />
</p>

[![](https://jitpack.io/v/afreakyelf/Pdf-Viewer.svg)](https://jitpack.io/#afreakyelf/Pdf-Viewer) [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/Apache-2.0) [![HitCount](http://hits.dwyl.com/afreakyelf/https://githubcom/afreakyelf/Pdf-Viewer.svg)](http://hits.dwyl.com/afreakyelf/https://githubcom/afreakyelf/Pdf-Viewer)


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

**This project needs you!** If you would like to support this project's further development, the creator of this project or the continuous maintenance of this project, **feel free to donate**. Your donation is highly appreciated (and I love food, Tea and beer). Thank you!

**PayPal**

- [**Donate 5 $**](https://www.paypal.me/afreakyelf): Thank's for creating this project, here's a Tea (or some beer) for you!
- [**Donate 10 $**](https://www.paypal.me/afreakyelf): Wow, I am stunned. Let me take you to the movies!
- [**Donate 15 $**](https://www.paypal.me/afreakyelf): I really appreciate your work, let's grab some lunch!
- [**Donate 25 $**](https://www.paypal.me/afreakyelf): That's some awesome stuff you did right there, dinner is on me!
- Or you can also [**choose what you want to donate**](https://www.paypal.me/afreakyelf), all donations are awesome!

## Author
Maintained by [Rajat Mittal](https://www.github.com/afreakyelf)
