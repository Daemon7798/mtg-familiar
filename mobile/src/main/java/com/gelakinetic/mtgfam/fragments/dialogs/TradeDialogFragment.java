/*
 * Copyright 2017 Adam Feinstein
 *
 * This file is part of MTG Familiar.
 *
 * MTG Familiar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MTG Familiar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MTG Familiar.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.gelakinetic.mtgfam.fragments.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.gelakinetic.mtgfam.R;
import com.gelakinetic.mtgfam.fragments.CardViewPagerFragment;
import com.gelakinetic.mtgfam.fragments.TradeFragment;
import com.gelakinetic.mtgfam.helpers.MtgCard;
import com.gelakinetic.mtgfam.helpers.PreferenceAdapter;
import com.gelakinetic.mtgfam.helpers.ToastWrapper;
import com.gelakinetic.mtgfam.helpers.database.CardDbAdapter;
import com.gelakinetic.mtgfam.helpers.database.DatabaseManager;
import com.gelakinetic.mtgfam.helpers.database.FamiliarDbException;
import com.gelakinetic.mtgfam.helpers.tcgp.MarketPriceInfo;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Class that creates dialogs for TradeFragment
 */
public class TradeDialogFragment extends FamiliarDialogFragment {

    /* Dialog Constants */
    public static final int DIALOG_UPDATE_CARD = 1;
    public static final int DIALOG_PRICE_SETTING = 2;
    public static final int DIALOG_SAVE_TRADE = 3;
    public static final int DIALOG_LOAD_TRADE = 4;
    public static final int DIALOG_DELETE_TRADE = 5;
    public static final int DIALOG_CONFIRMATION = 6;
    private static final int DIALOG_CHANGE_SET = 7;
    public static final int DIALOG_SORT = 8;

    /* Extra argument keys */
    public static final String ID_POSITION = "Position";
    public static final String ID_SIDE = "Side";

    /**
     * @return The currently viewed TradeFragment
     */
    @Nullable
    private TradeFragment getParentTradeFragment() {
        try {
            return (TradeFragment) getParentFamiliarFragment();
        } catch (ClassCastException e) {
            return null;
        }
    }

    @NotNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (!canCreateDialog()) {
            setShowsDialog(false);
            return DontShowDialog();
        }

        /* We're setting this to false if we return null, so we should reset it every time to be safe */
        setShowsDialog(true);
        mDialogId = getArguments().getInt(ID_KEY);
        final int sideForDialog = getArguments().getInt(ID_SIDE);
        final int positionForDialog = getArguments().getInt(ID_POSITION);

        if (null == getParentTradeFragment()) {
            return DontShowDialog();
        }

        switch (mDialogId) {
            case DIALOG_UPDATE_CARD: {
                /* Get some final references */
                final ArrayList<MtgCard> lSide = (sideForDialog == TradeFragment.LEFT ? getParentTradeFragment().mListLeft : getParentTradeFragment().mListRight);
                final TradeFragment.TradeDataAdapter aaSide = (TradeFragment.TradeDataAdapter) (sideForDialog == TradeFragment.LEFT ? getParentTradeFragment().getCardDataAdapter(TradeFragment.LEFT) : getParentTradeFragment().getCardDataAdapter(TradeFragment.RIGHT));
                if (positionForDialog >= lSide.size() || positionForDialog < 0) {
                    return DontShowDialog();
                }
                final boolean oldFoil = lSide.get(positionForDialog).mIsFoil;

                /* Inflate the view and pull out UI elements */
                @SuppressLint("InflateParams") View view = LayoutInflater.from(getActivity()).inflate(R.layout.trader_card_click_dialog,
                        null, false);
                assert view != null;
                final CheckBox foilCheckbox = view.findViewById(R.id.traderDialogFoil);
                final EditText numberOf = view.findViewById(R.id.traderDialogNumber);
                final EditText priceText = view.findViewById(R.id.traderDialogPrice);

                /* Set initial values */
                String numberOfStr = String.valueOf(lSide.get(positionForDialog).mNumberOf);
                numberOf.setText(numberOfStr);
                numberOf.setSelection(numberOfStr.length());
                foilCheckbox.setChecked(oldFoil);
                String priceNumberStr = lSide.get(positionForDialog).hasPrice() ?
                        lSide.get(positionForDialog).getPriceString().substring(1) : "";
                priceText.setText(priceNumberStr);
                priceText.setSelection(priceNumberStr.length());

                /* Only show the foil checkbox if the card can be foil */
                try {
                    SQLiteDatabase database = DatabaseManager.getInstance(getActivity(), false).openDatabase(false);
                    if (CardDbAdapter.canBeFoil(lSide.get(positionForDialog).mExpansion, database)) {
                        view.findViewById(R.id.checkbox_layout).setVisibility(View.VISIBLE);
                    } else {
                        view.findViewById(R.id.checkbox_layout).setVisibility(View.GONE);
                    }
                } catch (FamiliarDbException e) {
                    /* Err on the side of foil */
                    foilCheckbox.setVisibility(View.VISIBLE);
                }
                DatabaseManager.getInstance(getActivity(), false).closeDatabase(false);

                /* when the user checks or un-checks the foil box, if the price isn't custom, set it */
                foilCheckbox.setOnCheckedChangeListener((compoundButton, b) -> {
                    lSide.get(positionForDialog).mIsFoil = b;
                    if (!lSide.get(positionForDialog).mIsCustomPrice) {
                        getParentTradeFragment().loadPrice(lSide.get(positionForDialog));
                        priceText.setText(lSide.get(positionForDialog).hasPrice() ?
                                lSide.get(positionForDialog).getPriceString().substring(1) : "");
                    }
                });

                /* Set up the button to remove this card from the trade */
                view.findViewById(R.id.traderDialogRemove).setOnClickListener(v -> {
                    lSide.remove(positionForDialog);
                    aaSide.notifyDataSetChanged();
                    getParentTradeFragment().updateTotalPrices(sideForDialog);
                    getParentTradeFragment().removeDialog(getFragmentManager());
                });

                /* If this has a custom price, show the button to default the price */
                view.findViewById(R.id.traderDialogResetPrice).setOnClickListener(v -> {
                    lSide.get(positionForDialog).mIsCustomPrice = false;
                    /* This loads the price if necessary, or uses cached info */
                    getParentTradeFragment().loadPrice(lSide.get(positionForDialog));
                    int price = lSide.get(positionForDialog).mPrice;
                    priceText.setText(String.format(Locale.US, "%d.%02d", price / 100, price % 100));

                    aaSide.notifyDataSetChanged();
                    getParentTradeFragment().updateTotalPrices(sideForDialog);
                });

                /* Create the callback for when the dialog is successfully closed or when the card
                 * info is shown or when the set is changed
                 */
                MaterialDialog.SingleButtonCallback onPositiveCallback = (dialog, which) -> {
                    /* Grab a reference to the card */
                    MtgCard data = lSide.get(positionForDialog);

                    /* Assume non-custom price */
                    data.mIsCustomPrice = false;

                    /* Set this card's foil option */
                    data.mIsFoil = foilCheckbox.isChecked();

                    /* validate number of cards text */
                    if (numberOf.length() == 0) {
                        data.mNumberOf = 1;
                    } else {
                        /* Set the numberOf */
                        assert numberOf.getEditableText() != null;
                        try {
                            data.mNumberOf =
                                    (Integer.parseInt(numberOf.getEditableText().toString()));
                        } catch (NumberFormatException e) {
                            data.mNumberOf = 1;
                        }
                    }

                    /* validate the price text */
                    assert priceText.getText() != null;
                    String userInputPrice = priceText.getText().toString();

                    /* If the input price is blank, set it to zero */
                    if (userInputPrice.length() == 0) {
                        data.mIsCustomPrice = true;
                        data.mPrice = 0;
                    } else {
                        /* Attempt to parse the price */
                        try {
                            data.mPrice = (int) (Double.parseDouble(userInputPrice) * 100);
                        } catch (NumberFormatException e) {
                            data.mIsCustomPrice = true;
                            data.mPrice = 0;
                        }
                    }

                    /* Check if the user hand-modified the price by comparing the current price
                     * to the cached price */
                    int oldPrice;
                    if (data.mPriceInfo != null) {
                        oldPrice = (int) (data.mPriceInfo.getPrice(data.mIsFoil, getParentTradeFragment().getPriceSetting()) * 100);

                        if (oldPrice != data.mPrice) {
                            data.mIsCustomPrice = true;
                        }
                    } else {
                        data.mIsCustomPrice = true;
                    }

                    /* Notify things to update */
                    aaSide.notifyDataSetChanged();
                    getParentTradeFragment().updateTotalPrices(sideForDialog);
                };

                /* Set up the button to show info about this card */
                view.findViewById(R.id.traderDialogInfo).setOnClickListener(v -> {
                    try {
                        onPositiveCallback.onClick(null, null);
                        SQLiteDatabase database = DatabaseManager.getInstance(getActivity(), false).openDatabase(false);
                        /* Get the card ID, and send it to a new CardViewPagerFragment */
                        Cursor cursor = CardDbAdapter.fetchCardByNameAndSet(lSide.get(positionForDialog).mName,
                                lSide.get(positionForDialog).mExpansion, Collections.singletonList(
                                        CardDbAdapter.DATABASE_TABLE_CARDS + "." + CardDbAdapter.KEY_ID), database);

                        Bundle args = new Bundle();
                        args.putLongArray(CardViewPagerFragment.CARD_ID_ARRAY, new long[]{cursor.getLong(
                                cursor.getColumnIndex(CardDbAdapter.KEY_ID))});
                        args.putInt(CardViewPagerFragment.STARTING_CARD_POSITION, 0);

                        cursor.close();
                        CardViewPagerFragment cvpFrag = new CardViewPagerFragment();
                        getParentTradeFragment().startNewFragment(cvpFrag, args);
                    } catch (FamiliarDbException e) {
                        getParentTradeFragment().handleFamiliarDbException(false);
                    }
                    DatabaseManager.getInstance(getActivity(), false).closeDatabase(false);
                });

                /* Set up the button to change the set of this card */
                view.findViewById(R.id.traderDialogChangeSet).setOnClickListener(v -> {
                    onPositiveCallback.onClick(null, null);
                    getParentTradeFragment().showDialog(DIALOG_CHANGE_SET, sideForDialog, positionForDialog);
                });

                return new MaterialDialog.Builder(this.getActivity())
                        .title(lSide.get(positionForDialog).mName)
                        .customView(view, false)
                        .positiveText(R.string.dialog_done)
                        .onPositive(onPositiveCallback)
                        .negativeText(R.string.dialog_cancel)
                        .onNegative((dialog, which) -> {
                            // Revert any foil changes
                            lSide.get(positionForDialog).mIsFoil = oldFoil;
                            if (!lSide.get(positionForDialog).mIsCustomPrice) {
                                getParentTradeFragment().loadPrice(lSide.get(positionForDialog));
                                priceText.setText(lSide.get(positionForDialog).hasPrice() ?
                                        lSide.get(positionForDialog).getPriceString().substring(1) : "");
                            }
                        })
                        .build();
            }
            case DIALOG_CHANGE_SET: {
                /* Make sure positionForDialog is in bounds */
                int max;
                if (sideForDialog == TradeFragment.LEFT) {
                    max = getParentTradeFragment().mListLeft.size();
                } else {
                    max = getParentTradeFragment().mListRight.size();
                }
                if (positionForDialog < 0 || positionForDialog >= max) {
                    return DontShowDialog();
                }

                /* Get the card */
                MtgCard data = (sideForDialog == TradeFragment.LEFT ?
                        getParentTradeFragment().mListLeft.get(positionForDialog) : getParentTradeFragment().mListRight.get(positionForDialog));

                Set<String> sets = new LinkedHashSet<>();
                Set<String> setCodes = new LinkedHashSet<>();
                try {
                    SQLiteDatabase database = DatabaseManager.getInstance(getActivity(), false).openDatabase(false);
                    /* Query the database for all versions of this card */
                    Cursor cards = CardDbAdapter.fetchCardByName(data.mName, Arrays.asList(
                            CardDbAdapter.DATABASE_TABLE_CARDS + "." + CardDbAdapter.KEY_ID,
                            CardDbAdapter.DATABASE_TABLE_CARDS + "." + CardDbAdapter.KEY_SET,
                            CardDbAdapter.DATABASE_TABLE_SETS + "." + CardDbAdapter.KEY_NAME), true, false, database);
                    /* Build set names and set codes */
                    while (!cards.isAfterLast()) {
                        if (sets.add(cards.getString(cards.getColumnIndex(CardDbAdapter.KEY_NAME)))) {
                            setCodes.add(cards.getString(cards.getColumnIndex(CardDbAdapter.KEY_SET)));
                        }
                        cards.moveToNext();
                    }
                    /* clean up */
                    cards.close();
                } catch (FamiliarDbException e) {
                    /* Don't show the dialog, but pop a toast */
                    getParentTradeFragment().handleFamiliarDbException(true);
                    DatabaseManager.getInstance(getActivity(), false).closeDatabase(false);
                    return DontShowDialog();
                }

                DatabaseManager.getInstance(getActivity(), false).closeDatabase(false);

                /* Turn set names and set codes into arrays */
                final String[] aSets = sets.toArray(new String[sets.size()]);
                final String[] aSetCodes = setCodes.toArray(new String[setCodes.size()]);

                /* Build and return the dialog */
                return new MaterialDialog.Builder(getActivity())
                        .title(R.string.card_view_set_dialog_title)
                        .items((CharSequence[]) aSets)
                        .itemsCallback((dialog, itemView, position, text) -> {
                            /* Figure out what we're updating */
                            MtgCard data1;
                            TradeFragment.TradeDataAdapter adapter;

                            /* Make sure positionForDialog is in bounds */
                            int max1;
                            if (sideForDialog == TradeFragment.LEFT) {
                                max1 = getParentTradeFragment().mListLeft.size();
                            } else {
                                max1 = getParentTradeFragment().mListRight.size();
                            }
                            if (positionForDialog < 0 || positionForDialog >= max1) {
                                return;
                            }

                            if (sideForDialog == TradeFragment.LEFT) {
                                data1 = getParentTradeFragment().mListLeft.get(positionForDialog);
                                adapter = (TradeFragment.TradeDataAdapter) getParentTradeFragment().getCardDataAdapter(TradeFragment.LEFT);
                            } else {
                                data1 = getParentTradeFragment().mListRight.get(positionForDialog);
                                adapter = (TradeFragment.TradeDataAdapter) getParentTradeFragment().getCardDataAdapter(TradeFragment.RIGHT);
                            }

                            /* Change the card's information, and reload the price */
                            data1.mExpansion = (aSetCodes[position]);
                            data1.mSetName = (aSets[position]);
                            data1.mMessage = (getString(R.string.wishlist_loading));
                            data1.mPriceInfo = null;

                            /* See if the new set can be foil */
                            try {
                                SQLiteDatabase database = DatabaseManager.getInstance(getActivity(), false).openDatabase(false);
                                if (!CardDbAdapter.canBeFoil(data1.mExpansion, database)) {
                                    data1.mIsFoil = false;
                                }
                            } catch (FamiliarDbException e) {
                                data1.mIsFoil = false;
                            }
                            DatabaseManager.getInstance(getActivity(), false).closeDatabase(false);

                            /* Reload and notify the adapter */
                            getParentTradeFragment().loadPrice(data1);
                            adapter.notifyDataSetChanged();
                        })
                        .build();
            }
            case DIALOG_PRICE_SETTING: {
                /* Build the dialog with some choices */
                return new MaterialDialog.Builder(this.getActivity())
                        .title(R.string.pref_trade_price_title)
                        .items(getResources().getStringArray(R.array.trade_option_entries))
                        .itemsCallbackSingleChoice(getParentTradeFragment().getPriceSetting().ordinal(), (dialog, itemView, which, text) -> {
                            if (getParentTradeFragment().getPriceSetting().ordinal() != which) {
                                getParentTradeFragment().setPriceSetting(MarketPriceInfo.PriceType.fromOrdinal(which));

                                /* Update ALL the prices! */
                                for (MtgCard data : getParentTradeFragment().mListLeft) {
                                    if (!data.mIsCustomPrice) {
                                        data.mMessage = getString(R.string.wishlist_loading);
                                        getParentTradeFragment().loadPrice(data);
                                    }
                                }
                                getParentTradeFragment().getCardDataAdapter(TradeFragment.LEFT).notifyDataSetChanged();

                                for (MtgCard data : getParentTradeFragment().mListRight) {
                                    if (!data.mIsCustomPrice) {
                                        data.mMessage = getString(R.string.wishlist_loading);
                                        getParentTradeFragment().loadPrice(data);
                                    }
                                }
                                getParentTradeFragment().getCardDataAdapter(TradeFragment.RIGHT).notifyDataSetChanged();

                                /* And also update the preference */
                                PreferenceAdapter.setTradePrice(getContext(), getParentTradeFragment().getPriceSetting());

                                getParentTradeFragment().updateTotalPrices(TradeFragment.BOTH);
                            }
                            dialog.dismiss();
                            return true;
                        })
                        .build();
            }
            case DIALOG_SAVE_TRADE: {
                /* Inflate a view to type in the trade's name, and show it in an AlertDialog */
                @SuppressLint("InflateParams") View textEntryView = getActivity().getLayoutInflater()
                        .inflate(R.layout.alert_dialog_text_entry, null, false);
                assert textEntryView != null;
                final EditText nameInput = textEntryView.findViewById(R.id.text_entry);
                nameInput.append(getParentTradeFragment().mCurrentTrade);
                /* Set the button to clear the text field */
                textEntryView.findViewById(R.id.clear_button).setOnClickListener(view -> nameInput.setText(""));

                Dialog dialog = new MaterialDialog.Builder(getActivity())
                        .title(R.string.trader_save)
                        .customView(textEntryView, false)
                        .positiveText(R.string.dialog_ok)
                        .onPositive((dialog1, which) -> {
                            if (nameInput.getText() == null) {
                                return;
                            }
                            String tradeName = nameInput.getText().toString();

                            /* Don't bother saving if there is no name */
                            if (tradeName.length() == 0 || tradeName.equals("")) {
                                return;
                            }

                            getParentTradeFragment().saveTrade(tradeName + TradeFragment.TRADE_EXTENSION);
                            getParentTradeFragment().mCurrentTrade = tradeName;
                        })
                        .negativeText(R.string.dialog_cancel)
                        .build();
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                return dialog;
            }
            case DIALOG_LOAD_TRADE: {

                final String[] tradeNames = getFiles(TradeFragment.TRADE_EXTENSION);

                /* If there are no files, don't show the dialog */
                if (tradeNames.length == 0) {
                    ToastWrapper.makeAndShowText(this.getActivity(), R.string.trader_toast_no_trades, ToastWrapper.LENGTH_LONG);
                    return DontShowDialog();
                }

                return new MaterialDialog.Builder(this.getActivity())
                        .title(R.string.trader_load)
                        .negativeText(R.string.dialog_cancel)
                        .items((CharSequence[]) tradeNames)
                        .itemsCallback((dialog, itemView, position, text) -> {
                            /* Load the trade, set the current trade name */
                            getParentTradeFragment().loadTrade(tradeNames[position] + TradeFragment.TRADE_EXTENSION);
                            getParentTradeFragment().mCurrentTrade = tradeNames[position];

                            /* Alert things to update */
                            getParentTradeFragment().getCardDataAdapter(TradeFragment.LEFT).notifyDataSetChanged();
                            getParentTradeFragment().getCardDataAdapter(TradeFragment.RIGHT).notifyDataSetChanged();
                            getParentTradeFragment().updateTotalPrices(TradeFragment.BOTH);
                        })
                        .build();
            }
            case DIALOG_DELETE_TRADE: {

                final String[] tradeNames = getFiles(TradeFragment.TRADE_EXTENSION);

                /* If there are no files, don't show the dialog */
                if (tradeNames.length == 0) {
                    ToastWrapper.makeAndShowText(this.getActivity(), R.string.trader_toast_no_trades, ToastWrapper.LENGTH_LONG);
                    return DontShowDialog();
                }

                return new MaterialDialog.Builder(this.getActivity())
                        .title(R.string.trader_delete)
                        .negativeText(R.string.dialog_cancel)
                        .items((CharSequence[]) tradeNames)
                        .itemsCallback((dialog, itemView, position, text) -> {
                            File toDelete = new File(getActivity().getFilesDir(), tradeNames[position] +
                                    TradeFragment.TRADE_EXTENSION);
                            if (!toDelete.delete()) {
                                ToastWrapper.makeAndShowText(getActivity(), toDelete.getName() + " " +
                                        getString(R.string.not_deleted), ToastWrapper.LENGTH_LONG);
                            }
                        })
                        .build();
            }
            case DIALOG_CONFIRMATION: {
                return new MaterialDialog.Builder(this.getActivity())
                        .title(R.string.trader_clear_dialog_title)
                        .content(R.string.trader_clear_dialog_text)
                        .positiveText(R.string.dialog_ok)
                        .onPositive((dialog, which) -> {
                            /* Clear the arrays and tell everything to update */
                            getParentTradeFragment().mCurrentTrade = "";
                            getParentTradeFragment().mListRight.clear();
                            getParentTradeFragment().mListLeft.clear();
                            getParentTradeFragment().getCardDataAdapter(TradeFragment.RIGHT).notifyDataSetChanged();
                            getParentTradeFragment().getCardDataAdapter(TradeFragment.LEFT).notifyDataSetChanged();
                            getParentTradeFragment().updateTotalPrices(TradeFragment.BOTH);
                            getParentTradeFragment().clearCardNameInput();
                            getParentTradeFragment().clearCardNumberInput();
                            getParentTradeFragment().uncheckFoilCheckbox();
                            dialog.dismiss();
                        })
                        .negativeText(R.string.dialog_cancel)
                        .cancelable(true)
                        .build();
            }
            default: {
                savedInstanceState.putInt("id", mDialogId);
                return super.onCreateDialog(savedInstanceState);
            }
        }
    }

}