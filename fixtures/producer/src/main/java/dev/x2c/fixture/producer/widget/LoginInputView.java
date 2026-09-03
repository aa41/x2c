package dev.x2c.fixture.producer.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/** Resource-free login input used to exercise custom constructors and typed XML setters. */
public final class LoginInputView extends EditText {
    public LoginInputView(Context context) {
        super(context);
        setSingleLine(true);
        setTextColor(0xFF182033);
        setHintTextColor(0xFF98A2B3);
        setTextSize(15f);
        setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        setBackgroundColor(Color.TRANSPARENT);
        setSelectAllOnFocus(false);
    }

    public void setHintText(CharSequence hint) {
        setHint(hint);
    }

    public void setInputMode(int inputType) {
        setInputType(inputType);
        setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        if (inputType == 129) {
            setImeOptions(EditorInfo.IME_ACTION_DONE);
        } else {
            setImeOptions(EditorInfo.IME_ACTION_NEXT);
        }
    }
}
