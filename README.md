<h1 align="center">Pdf Viewer For Android</h1>

<p align="center">
A Simple PDF Viewer library which only occupies around <b>80kb</b> while most of the Pdf viewer occupies upto <b>16MB</b> space.
<br>
<br>
</p>
<p float="left">
  <img src="https://github.com/afreakyelf/Pdf-Viewer/assets/38572147/e310060c-bea2-42ee-b02a-3758f3122e05" width="199" />
  <img src="https://github.com/afreakyelf/Pdf-Viewer/assets/38572147/13f64593-7627-48bc-b573-54cebb0651b2" width="199" />
  <img src="https://github.com/afreakyelf/Pdf-Viewer/assets/38572147/fa6a0ff9-11dd-4087-bf7e-d4ba6795386c" width="199" />
  <img src="https://github.com/afreakyelf/Pdf-Viewer/assets/38572147/babde964-5373-4d03-85ad-1b8c2cc0ab29" width="199" />
  <img src="https://github.com/afreakyelf/Pdf-Viewer/assets/38572147/4344c962-88f7-4be4-8935-50370ad6752d" width="199" />

</p>


[![](https://jitpack.io/v/afreakyelf/Pdf-Viewer.svg)](https://jitpack.io/#afreakyelf/Pdf-Viewer) [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/Apache-2.0) ![](https://img.shields.io/github/forks/afreakyelf/Pdf-Viewer?label=Forks)
![](https://img.shields.io/github/stars/afreakyelf/Pdf-Viewer?label=Stars&color=9cf) ![Visitors](https://api.visitorbadge.io/api/visitors?path=https%3A%2F%2Fgithub.com%2Fafreakyelf%2FPdf-Viewer&label=Visitors&countColor=%23263759&style=flat) [![CodeFactor](https://www.codefactor.io/repository/github/afreakyelf/pdf-viewer/badge)](https://www.codefactor.io/repository/github/afreakyelf/pdf-viewer)

## ✨ Major Enhancements in Our PDF Viewer Library ✨
Hello Developers! We're thrilled to share some significant enhancements we've made to our PDF viewer library. We've fine-tuned several aspects to enhance your experience and ensure top-notch performance and security. Here's what's new:

- ### Jetpack Compose Ready 🚀
  Step into the future with Jetpack Compose compatibility. Integrating our PDF viewer in Compose projects is now effortless, thanks to the PdfRendererViewCompose composable function.
- ### Turbocharged Performance 🏎️
  We've optimized performance to handle PDFs more efficiently, ensuring swift and smooth operations, even with large documents.
- ### Local and on device files 📁
  We have made it better and smooth with how local files are handled now, with latest permission policies. 
- ### Seamless Orientation Adaptation 🔄
    Our library now smartly preserves your page position during orientation changes, ensuring uninterrupted reading sessions. 
- ### Enhanced File Path Security 🔐
    Security just got stronger. We've revamped our file path handling to provide robust protection against directory traversal attacks, keeping your data safer than ever.
- ### Streamlined Caching System 💾
    Experience efficiency at its best! Our refined caching strategy smartly manages storage, retaining only the most recent PDF file to optimize performance and space usage.
- ### Discreet Screenshot Prevention Feature 🚫📸
    Privacy matters. Our new screenshot-blocking feature enhances data confidentiality in your app, keeping sensitive information secure from prying eyes.
- ### Flexible UI Customization ✨
    Your design, your rules. Enjoy complete freedom in customizing the PDF viewer's interface, ensuring a perfect match with your app's style and theme. Render the view directly in your screen now.
- ### 'NoActionBar' Theme Compatibility 🎨
    Seamless aesthetics, no matter the theme. Our library now gracefully integrates with 'NoActionBar' themes, ensuring a cohesive and appealing user interface.

Stay tuned as we continue to innovate and improve. Happy coding, and let's keep creating amazing experiences together!

## How to integrate into your app?
Integrating the project is simple, All you need to do is follow the below steps
> ⚠️ NOTE: Replace the Tag with current release version, i.e  [![](https://jitpack.io/v/afreakyelf/Pdf-Viewer.svg)](https://jitpack.io/#afreakyelf/Pdf-Viewer)

### Kotlin DSL

Step 1. Add the JitPack repository to your build file. Add it in your root `settings.gradle.kts` at the end of repositories:

```kotlin
dependencyResolutionManagement {
    ....
    repositories {
        ...
        maven { setUrl( "https://jitpack.io") }
    }
}
```

Step 2. Add the dependency
```kotlin
dependencies {
    implementation ("com.github.afreakyelf:Pdf-Viewer:v{Tag}")
}
```

### Groovy DSL

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



## How to use the library?
Now you have integrated the library in your project but **how do you use it**? Well its really easy just launch the intent with in following way: (Refer to [MainActivity.kt](https://github.com/afreakyelf/Pdf-Viewer/blob/master/app/src/main/java/com/rajat/sample/pdfviewer/MainActivity.kt) for more details.)

### Prerequisites
Ensure the library is included in your project's dependencies.

### Launching PDF Viewer

#### Open PDF from a URL
To display a PDF from a URL, use the following code:

```kotlin
/* Parameters:
- context: The context of your activity.
- pdfUrl: URL of the PDF to be displayed.
- pdfTitle: Title of the PDF document.
- saveTo: Determines how to handle saving the PDF (e.g., ASK_EVERYTIME prompts the user each time).
- enableDownload: Enables downloading of the PDF. */

PdfViewerActivity.launchPdfFromUrl(
    context = this,
    pdfUrl = "your_pdf_url_here",
    pdfTitle = "PDF Title",
    saveTo = PdfViewerActivity.saveTo.ASK_EVERYTIME,
    enableDownload = true
)
```

#### Open PDF from Local Storage
To open a PDF stored in local storage:

```kotlin
/* Parameters:
- path: File path or URI of the local PDF.
- fromAssets: Set to false when loading from local storage. // FALSE by default
*/

PdfViewerActivity.launchPdfFromPath(
    context = this,
    path = "your_file_path_or_uri_here",
    pdfTitle = "Title",
    saveTo = PdfViewerActivity.saveTo.ASK_EVERYTIME,
    fromAssets = false
)
```

#### Open PDF from Assets
To open a PDF from the app's assets folder:

```kotlin
/* Parameters:
- path: File path or URI of the local PDF.
- fromAssets: Set to true when loading from assets.
*/

PdfViewerActivity.launchPdfFromPath(
  context = this,
  path = "file_name_in_assets",
  pdfTitle = "Title",
  saveTo = PdfViewerActivity.saveTo.ASK_EVERYTIME,
  fromAssets = true
)
```

#### Loading PDF in a View
Load a PDF directly into a view:

Add PDF render view in your layout file

```xml
<com.rajat.pdfviewer.PdfRendererView
    android:id="@+id/pdfView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:pdfView_divider="@drawable/pdf_viewer_divider"
    app:pdfView_showDivider="false" />
```
and in your kotlin file
```kotlin
binding.pdfView.initWithUrl(
  url = "your_pdf_url_here",
  lifecycleCoroutineScope = lifecycleScope,
  lifecycle = lifecycle
)

```

#### Using with Jetpack Compose
For Jetpack Compose, utilize PdfRendererViewCompose:

```kotlin
PdfRendererViewCompose(
    url = "your_pdf_url_here",
    lifecycleOwner = LocalLifecycleOwner.current
)
```

That's pretty much it and you're all wrapped up.

### Ui Customizations
You need to add the custom theme to styles.xml/themes.xml file and override the required attribute values.
Parent theme can be either **Theme.PdfView.Light** or **Theme.PdfView.Dark** or the one with no actionbar from the application.
Note: If parent is not one of the themes from this library, all of the pdfView attributes should be added to that theme.

    <style name="Theme.PdfView.SelectedTheme" parent="@style/Theme.PdfView.Light">
        <item name="pdfView_backIcon">@drawable/ic_arrow_back</item>
        <item name="pdfView_showToolbar">true</item>
        <item name="pdfView_disableScreenshots">true</item>
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

## Who's using Pdf-Viewer?
**👉 [Check out who's using Pdf-Viewer](/usecases.md)**

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
