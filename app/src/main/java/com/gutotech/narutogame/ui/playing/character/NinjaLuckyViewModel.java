package com.gutotech.narutogame.ui.playing.character;

import androidx.annotation.IntDef;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.gutotech.narutogame.R;
import com.gutotech.narutogame.data.firebase.FirebaseFunctionsUtils;
import com.gutotech.narutogame.data.model.Attribute;
import com.gutotech.narutogame.data.model.LotteryItem;
import com.gutotech.narutogame.data.model.CharOn;
import com.gutotech.narutogame.data.model.NinjaLucky;
import com.gutotech.narutogame.data.model.Ramen;
import com.gutotech.narutogame.data.model.Scroll;
import com.gutotech.narutogame.data.model.Village;
import com.gutotech.narutogame.data.repository.Callback;
import com.gutotech.narutogame.data.repository.CharacterRepository;
import com.gutotech.narutogame.data.repository.NinjaLuckyRepository;
import com.gutotech.narutogame.utils.SingleLiveEvent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class NinjaLuckyViewModel extends ViewModel {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({RYOUS_DAILY, RYOUS_WEEKLY})
    @interface PlayMode {
    }

    public static final int RYOUS_DAILY = 500;
    public static final int RYOUS_WEEKLY = 1500;

    private int mTotalItems;
    private int[] mIntervals;

    private MutableLiveData<Integer> mPlayModeSelected;

    private MutableLiveData<List<LotteryItem>> mLotteryItems;
    private LotteryItem mLotteryItemReceived;

    private SingleLiveEvent<Void> mStartAnimationEvent = new SingleLiveEvent<>();
    private SingleLiveEvent<Integer> mShowPremiumEvent = new SingleLiveEvent<>();
    private SingleLiveEvent<Integer> mShowWarningDialogEvent = new SingleLiveEvent<>();

    private MutableLiveData<List<Boolean>> mDaysOfWeek = new MutableLiveData<>();

    private NinjaLucky mNinjaLucky;

    private final NinjaLuckyRepository mNinjaLuckyRepository = NinjaLuckyRepository.getInstance();

    private int mDayOfWeek;

    public NinjaLuckyViewModel() {
        mNinjaLuckyRepository.get(CharOn.character.getId(), ninjaLucky -> {
            mNinjaLucky = ninjaLucky;
            mDaysOfWeek.postValue(mNinjaLucky.getDaysOfWeek());
        });

        mPlayModeSelected = new MutableLiveData<>(RYOUS_DAILY);

        mLotteryItems = new MutableLiveData<>(buildItems());

        mTotalItems = mLotteryItems.getValue().size();

        mIntervals = new int[mTotalItems];

        calculateIntervals();
    }

    public void onPlayModeSelected(@PlayMode int mode) {
        mPlayModeSelected.setValue(mode);
    }

    public void onPlayButtonPressed() {
        validate(result -> {
            if (result) {
                play();
            }
        });
    }

    private void validate(Callback<Boolean> callback) {
        FirebaseFunctionsUtils.getServerTime(currentTimestamp -> {
            synchronized (NinjaLuckyViewModel.this) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(currentTimestamp);
                mDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

                if (mNinjaLucky == null) {
                    callback.call(false);
                    return;
                }

                if (mPlayModeSelected.getValue() == RYOUS_DAILY) {
                    if (mNinjaLucky.played(mDayOfWeek)) {
                        mShowWarningDialogEvent.setValue(R.string.warning_already_played_today);
                        callback.call(false);
                        return;
                    }

                    if (CharOn.character.getRyous() >= RYOUS_DAILY) {
                        CharOn.character.subRyous(RYOUS_DAILY);
                        callback.call(true);
                        return;
                    } else {
                        mShowWarningDialogEvent.setValue(R.string.warning_dont_have_enough_ryous);
                    }
                } else if (mNinjaLucky.playedAllDays()) {
                    if (CharOn.character.getRyous() >= RYOUS_WEEKLY) {
                        CharOn.character.subRyous(RYOUS_WEEKLY);
                        callback.call(true);
                        return;
                    } else {
                        mShowWarningDialogEvent.setValue(R.string.warning_dont_have_enough_ryous);
                    }
                } else {
                    mShowWarningDialogEvent.setValue(R.string.warning_havent_played_the_entire_week_yet);
                }

                callback.call(false);
            }
        });
    }

    private void play() {
        mStartAnimationEvent.call();

        mLotteryItemReceived = generateItem();
        mLotteryItemReceived.getPremium().receive();
        CharacterRepository.getInstance().save(CharOn.character);

        mNinjaLucky.selectDayAsPlayed(mDayOfWeek);
        mNinjaLuckyRepository.save(CharOn.character.getId(), mNinjaLucky);
        mDaysOfWeek.postValue(mNinjaLucky.getDaysOfWeek());
    }

    void onAnimationEnd() {
        mShowPremiumEvent.setValue(mLotteryItemReceived.getDescription());
    }

    private void calculateIntervals() {
        mIntervals[0] = mLotteryItems.getValue().get(0).getChancesOfWin();

        for (int i = 1; i < mTotalItems; i++) {
            mIntervals[i] = mIntervals[i - 1] + mLotteryItems.getValue().get(i).getChancesOfWin();
        }
    }

    private LotteryItem generateItem() {
        int n = new SecureRandom().nextInt(mIntervals[mTotalItems - 1]) + 1;

        int generatedItemIndex = 0;

        if (n > mIntervals[0]) {
            for (int i = 1; i < mTotalItems; i++) {
                if (n > mIntervals[i - 1] && n <= mIntervals[i]) {
                    generatedItemIndex = i;
                    break;
                }
            }
        }

        return mLotteryItems.getValue().get(generatedItemIndex);
    }

    private List<LotteryItem> buildItems() {
        List<LotteryItem> mLotteryItems = new ArrayList<>();

        mLotteryItems.add(new LotteryItem("1", R.string.ryou1, 1,
                () -> CharOn.character.addRyous(1)));
        mLotteryItems.add(new LotteryItem("3", R.string.ryous2000, 50,
                () -> CharOn.character.addRyous(2000)));
        mLotteryItems.add(new LotteryItem("4", R.string.ryous5000, 20,
                () -> CharOn.character.addRyous(5000)));
        mLotteryItems.add(new LotteryItem("5", R.string.ryous10000, 10,
                () -> CharOn.character.addRyous(10000)));
        mLotteryItems.add(new LotteryItem("6", R.string.ryous25000, 5,
                () -> CharOn.character.addRyous(25000)));
        mLotteryItems.add(new LotteryItem("7", R.string.ryous50000, 1,
                () -> CharOn.character.addRyous(50000)));

        mLotteryItems.add(new LotteryItem("9", R.string.exppoints1, 1,
                () -> CharOn.character.incrementExp(1)));
        mLotteryItems.add(new LotteryItem("11", R.string.exppoints1500, 50,
                () -> CharOn.character.incrementExp(1500)));
        mLotteryItems.add(new LotteryItem("12", R.string.exppoints2000, 40,
                () -> CharOn.character.incrementExp(2000)));

        if (CharOn.character.getNumberOfDaysPlayed() >= 7) {
            mLotteryItems.add(new LotteryItem("13", R.string.exppoints2500, 30,
                    () -> CharOn.character.incrementExp(2500)));
            mLotteryItems.add(new LotteryItem("14", R.string.exppoints3000, 20,
                    () -> CharOn.character.incrementExp(3000)));
            mLotteryItems.add(new LotteryItem("15", R.string.exppoints15000, 1,
                    () -> CharOn.character.incrementExp(15000)));
        }

        mLotteryItems.add(new LotteryItem("16", R.string.ninja_snack_1x, 1,
                () -> CharOn.character.getBag().addRamen(new Ramen(
                        "nissin", R.string.ninja_snack, R.string.ninja_snack_description,
                        25, 100), 1)
        ));
        mLotteryItems.add(new LotteryItem("18", R.string.missio_ebi_ramen_10x, 50,
                () -> CharOn.character.getBag().addRamen(new Ramen(
                        "Misso_Ebi-Ramen", R.string.shio_gyoza_ramen,
                        R.string.shio_gyoza_ramen_description,
                        420, 1800), 10)
        ));
        mLotteryItems.add(new LotteryItem("19", R.string.missio_ebi_ramen_20x, 30,
                () -> CharOn.character.getBag().addRamen(new Ramen(
                        "Misso_Ebi-Ramen", R.string.shio_gyoza_ramen,
                        R.string.shio_gyoza_ramen_description,
                        420, 1800), 20)
        ));
        mLotteryItems.add(new LotteryItem("20", R.string.missio_ebi_ramen_50x, 1,
                () -> CharOn.character.getBag().addRamen(new Ramen(
                        "Misso_Ebi-Ramen", R.string.shio_gyoza_ramen,
                        R.string.shio_gyoza_ramen_description,
                        420, 1800), 50)
        ));

        mLotteryItems.add(new LotteryItem("29", R.string.tai1, 30,
                () -> {
                    CharOn.character.getAttributes().earn(Attribute.TAI.id);
                    CharOn.character.updateFormulas();
                }));
        mLotteryItems.add(new LotteryItem("30", R.string.nin1, 30,
                () -> {
                    CharOn.character.getAttributes().earn(Attribute.NIN.id);
                    CharOn.character.updateFormulas();
                }));
        mLotteryItems.add(new LotteryItem("31", R.string.gen1, 30,
                () -> {
                    CharOn.character.getAttributes().earn(Attribute.GEN.id);
                    CharOn.character.updateFormulas();
                }));
        mLotteryItems.add(new LotteryItem("20388", R.string.buki1, 30,
                () -> {
                    CharOn.character.getAttributes().earn(Attribute.BUK.id);
                    CharOn.character.updateFormulas();
                }));
        mLotteryItems.add(new LotteryItem("32", R.string.agility1, 30,
                () -> {
                    CharOn.character.getAttributes().earn(Attribute.AGI.id);
                    CharOn.character.updateFormulas();
                }));
        mLotteryItems.add(new LotteryItem("33", R.string.seal1, 30,
                () -> {
                    CharOn.character.getAttributes().earn(Attribute.SEAL.id);
                    CharOn.character.updateFormulas();
                }));
        mLotteryItems.add(new LotteryItem("34", R.string.strenght1, 30,
                () -> {
                    CharOn.character.getAttributes().earn(Attribute.FOR.id);
                    CharOn.character.updateFormulas();
                }));
        mLotteryItems.add(new LotteryItem("35", R.string.inte1, 30,
                () -> {
                    CharOn.character.getAttributes().earn(Attribute.INTE.id);
                    CharOn.character.updateFormulas();
                }));
        mLotteryItems.add(new LotteryItem("36", R.string.res1, 30,
                () -> {
                    CharOn.character.getAttributes().earn(Attribute.RES.id);
                    CharOn.character.updateFormulas();
                }));
        mLotteryItems.add(new LotteryItem("20392", R.string.energy1, 30,
                () -> {
                    CharOn.character.getAttributes().earn(Attribute.ENER.id);
                    CharOn.character.updateFormulas();
                }));

        mLotteryItems.add(new LotteryItem("20368", R.string.scroll_konoha_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("1", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.FOLHA), 4))
        );
        mLotteryItems.add(new LotteryItem("20369", R.string.scroll_sand_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("2", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.AREIA), 4))
        );
        mLotteryItems.add(new LotteryItem("20370", R.string.scroll_mist_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("3", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.NEVOA), 4))
        );
        mLotteryItems.add(new LotteryItem("20371", R.string.scroll_stone_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("4", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.PEDRA), 4))
        );
        mLotteryItems.add(new LotteryItem("20372", R.string.scroll_cloud_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("5", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.NUVEM), 4))
        );
        mLotteryItems.add(new LotteryItem("20373", R.string.scroll_akatsuki_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("6", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.AKATSUKI), 4))
        );
        mLotteryItems.add(new LotteryItem("20374", R.string.scroll_sound_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("7", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.SOM), 4))
        );
        mLotteryItems.add(new LotteryItem("20375", R.string.scroll_rain_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("8", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.CHUVA), 4))
        );
        mLotteryItems.add(new LotteryItem("20382", R.string.scroll_snow_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("9", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.NEVE), 4))
        );
        mLotteryItems.add(new LotteryItem("20383", R.string.scroll_waterfall_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("10", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.CACHOEIRA), 4))
        );
        mLotteryItems.add(new LotteryItem("20384", R.string.scroll_hot_springs_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("11", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.FONTES_TERMAIS), 4))
        );
        mLotteryItems.add(new LotteryItem("20385", R.string.scroll_grass_20x, 5,
                () -> CharOn.character.getBag().addScroll(
                        new Scroll("12", R.string.scroll_to_leaf, R.string.scroll_to_leaf_des,
                                null, Village.GRAMA), 4))
        );

        return mLotteryItems;
    }

    LiveData<List<LotteryItem>> getLotteryItems() {
        return mLotteryItems;
    }

    public LiveData<Integer> getPlayModeSelected() {
        return mPlayModeSelected;
    }

    public LiveData<List<Boolean>> getDaysOfWeek() {
        return mDaysOfWeek;
    }

    LiveData<Void> getStartAnimationEvent() {
        return mStartAnimationEvent;
    }

    LiveData<Integer> getShowPremiumEvent() {
        return mShowPremiumEvent;
    }

    LiveData<Integer> getShowWarningDialogEvent() {
        return mShowWarningDialogEvent;
    }
}
