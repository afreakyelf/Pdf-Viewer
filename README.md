<h1 align="center">Pdf Viewer For Android</h1>

<p align="center">
A Simple PDF Viewer library which only occupies around <b>125kb</b> while most of the Pdf viewer occupies upto <b>16MB</b> space.
<br>
<br>
<img src="https://raw.githubusercontent.com/afreakyelf/Pdf-Viewer/master/Screenshot_2020-07-11-23-59-31-606_com.rajat.pdfviewer.jpg" width="420" height="840" />
</p>

[![](https://jitpack.io/v/afreakyelf/Pdf-Viewer.svg)](https://jitpack.io/#afreakyelf/Pdf-Viewer) [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/Apache-2.0) ![](https://img.shields.io/github/forks/afreakyelf/Pdf-Viewer?label=Forks)
![](https://img.shields.io/github/stars/afreakyelf/Pdf-Viewer?label=Stars&color=9cf) ![](https://visitor-badge.glitch.me/badge?page_id=afreakyelf.Pdf-Viewer)[![](https://jitci.com/gh/afreakyelf/Pdf-Viewer/svg)](https://jitci.com/gh/afreakyelf/Pdf-Viewer)

## Who's using Pdf-Viewer?
**👉 [Check out who's using Pdf-Viewer](/usecases.md)**

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
    implementation 'com.github.afreakyelf:Pdf-Viewer:v{Tag}'
}
```
NOTE: Replace the tag with current release version, e.g

```java
implementation 'com.github.afreakyelf:Pdf-Viewer:v1.0.7'
```

## How to use the library?
Now you have integrated the library in your project but **how do you use it**? Well its really easy just launch the intent with in following way:

### Kotlin
```kotlin
open_pdf.setOnClickListener {
            startActivity(
            
            // Use 'launchPdfFromPath' if you want to use assets file (enable "fromAssets" flag) / internal directory
           
                PdfViewerActivity.launchPdfFromUrl(           //PdfViewerActivity.Companion.launchPdfFromUrl(..   :: incase of JAVA       
                    context,                                                                      
                    "pdf_url",                                // PDF URL in String format
                    "Pdf title/name ",                        // PDF Name/Title in String format
                    "pdf directory to save",                  // If nothing specific, Put "" it will save to Downloads
                    enableDownload = false                    // This param is true by defualt.
                )
            )
        } 
```

### Java

```java
        open_pdf.setOnClickListener(view -> {
            startActivity(
            
            // Opening pdf from assets folder 
            
                    PdfViewerActivity.Companion.launchPdfFromPath(
                            this,
                            "file_name.pdf",
                            "Pdf title/name",
                            "assets",
                            false,
                            true
                    )
            );
        });

```

That's pretty much it and you're all wrapped up.

### Ui Customizations
You need to add the custom theme to styles.xml/themes.xml file and override the required attribute values.
Parent theme can be either **Theme.PdfView.Light** or **Theme.PdfView.Dark** or the one with no actionbar from the application.
Note: If parent is not one of the themes from this library, all of the pdfView attributes should be added to that theme.

    <style name="Theme.PdfView.SelectedTheme" parent="@style/Theme.PdfView.Light">
        <item name="pdfView_backIcon">@drawable/ic_arrow_back</item>
        ...
    </style>


#### Ui Customizations - Page number

You need to add the custom layout to pdf_view_page_no.xml file and override the required attribute
values.

    <?xml version="1.0" encoding="utf-8"?>  
    <TextView xmlns:android="http://schemas.android.com/apk/res/android"  
      android:id="@+id/pageNo"  
      android:layout_width="wrap_content"  
      android:layout_height="wrap_content"  
      android:layout_margin="18dp"  
      android:background="#9C27B0"  
      android:paddingStart="12dp"  
      android:paddingTop="4dp"  
      android:paddingEnd="12dp"  
      android:paddingBottom="4dp"  
      android:textColor="#ffffff"  
      android:textSize="16sp"  
      android:visibility="gone" />



#### Ui Page number

You need to add the custom string to strings.xml file and override the required strings.xml values.

Default:

    <string name="pdfView_page_no">%1$s of %2$s</string>

Custom:

    <string name="pdfView_page_no" >%1$s / %2$s</string>

#### Supported attributes

| Attribute Name | Type | Expected changes |
|--|--|--|
|pdfView_backIcon|drawable|Navigation icon|
|pdfView_downloadIcon|drawable|Download icon|
|pdfView_downloadIconTint|color|Download icon tint|
|pdfView_actionBarTint|color|Actionbar background color|
|pdfView_titleTextStyle|style|Actionbar title text appearance|
|pdfView_progressBar|style|Progress bar style|

## Contributing

Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/NewFeature`)
3. Commit your Changes (`git commit -m 'Add some NewFeature'`)
4. Push to the Branch (`git push origin feature/NewFeature`)
5. Open a Pull Request

## Donations
If this project help you reduce time to develop, you can give me a cup of coffee :)

[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/paypalme/afreakyelf)

## Author
Maintained by [Rajat Mittal](https://www.github.com/afreakyelf)
