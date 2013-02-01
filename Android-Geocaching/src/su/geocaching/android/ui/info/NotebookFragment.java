package su.geocaching.android.ui.info;

public class NotebookFragment extends AbstractWebViewFragment {
    @Override
    protected InfoViewModel.WebViewTabState getFragmentState() {
        return infoViewModel.getNotebookState();
    }

    @Override
    protected void BeginLoadData() {
        infoViewModel.beginLoadNotebook();
    }
}