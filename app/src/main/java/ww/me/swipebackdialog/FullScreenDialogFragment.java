package ww.me.swipebackdialog;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;

import ww.me.swipe_back_lib.SwipeBaseDialog;


public class FullScreenDialogFragment extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new MyDialog(getActivity());
    }


    class MyDialog extends SwipeBaseDialog {

        public MyDialog(@NonNull Context context) {
            super(context);
            setContentView( R.layout.dialog_layout);
        }

    }


}
