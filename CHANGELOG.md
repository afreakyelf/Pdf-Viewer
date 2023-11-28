# CHANGELOG

# [v2.0.0]

- **Orientation Change Handling:** Improved to maintain the current page position during device orientation changes.
- **Security Enhancement for File Paths:** Implemented secure file path handling to prevent directory traversal attacks.
- **Efficient Caching Strategy:** Optimized to store only the most recent PDF file in the cache, reducing storage usage.
- **Screenshot Prevention Feature:** Added functionality to disable screenshots and screen recordings for enhanced privacy.
- **Dynamic UI Customization:** Introduced flexibility in customizing the UI elements programmatically based on XML attributes.
- **Compatibility with 'NoActionBar' Theme:** Ensured default values for missing attributes, enabling smooth integration with various themes.
- **Jetpack Compose Support:** Added a composable function PdfRendererViewCompose for Jetpack Compose applications.
- **Performance Optimization:** Enhanced performance for better handling of large PDF files.

These updates focus on improving the user experience, security, and overall performance of the library.

# [v1.1.0]
- Added `pdfView_page_margin` as an attribute to change spacing (Right, Left, Top, Bottom) from the pages Or use the following attribute to add space for each edges:
```xml
<com.rajat.pdfviewer.PdfRendererView
    android:id="@+id/pdfView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:pdfView_divider="@drawable/divider"
    app:pdfView_engine="internal"
    app:pdfView_enableLoadingForPages="true"
    app:pdfView_page_marginRight="10dp"
    app:pdfView_page_marginLeft="10dp"
    app:pdfView_page_marginTop="5dp"
    app:pdfView_page_marginBottom="5dp"
/> 
```

- Added loading view for each pages. It's available by using `pdfView_enableLoadingForPages` attribute.
- Optimise rendering PDF process by using `CoroutineScope` instead of `GlobalScope`

## Breaking Changes
- Added night/light mode background for `PdfViewerActivity`. If you enable spacing it would show background of this activity.
- Remove background from the list of pages (`PinchZoomRecyclerView`) to let developers to add custom background for PDF viewer. It may affect to see the PDF viewer result by showing the parent background.