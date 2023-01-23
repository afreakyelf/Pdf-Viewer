# CHANGELOG

# v1.1.0
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