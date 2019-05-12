package org.nuclearfog.twidda.window;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener;

import org.nuclearfog.tag.Tagger.OnTagClickListener;
import org.nuclearfog.twidda.BuildConfig;
import org.nuclearfog.twidda.R;
import org.nuclearfog.twidda.adapter.FragmentAdapter;
import org.nuclearfog.twidda.adapter.FragmentAdapter.AdapterType;
import org.nuclearfog.twidda.backend.ProfileLoader;
import org.nuclearfog.twidda.backend.ProfileLoader.Mode;
import org.nuclearfog.twidda.database.GlobalSettings;

import java.text.NumberFormat;


public class UserProfile extends AppCompatActivity implements OnClickListener, OnTagClickListener, OnTabSelectedListener {

    private ProfileLoader profileAsync;
    private ViewPager pager;
    private TextView lnkTxt;
    private View[] icons;

    private boolean home, isFriend, isBlocked;
    private boolean isMuted, isLocked, canDm, requested;
    private String username;
    private long userId;

    private int tabIndex = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.page_profile);

        Bundle param = getIntent().getExtras();
        if (param != null && param.containsKey("userID") && param.containsKey("username")) {
            userId = param.getLong("userID");
            username = param.getString("username");
        } else if (BuildConfig.DEBUG) {
            throw new AssertionError();
        }

        Toolbar tool = findViewById(R.id.profile_toolbar);
        TabLayout tab = findViewById(R.id.profile_tab);
        TextView bioTxt = findViewById(R.id.bio);
        Button following = findViewById(R.id.following);
        Button follower = findViewById(R.id.follower);
        View root = findViewById(R.id.user_view);
        pager = findViewById(R.id.profile_pager);
        lnkTxt = findViewById(R.id.links);

        setSupportActionBar(tool);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayShowTitleEnabled(false);

        GlobalSettings settings = GlobalSettings.getInstance(this);
        home = userId == settings.getUserId();

        bioTxt.setMovementMethod(ScrollingMovementMethod.getInstance());
        tab.setSelectedTabIndicatorColor(settings.getHighlightColor());
        bioTxt.setLinkTextColor(settings.getHighlightColor());
        lnkTxt.setLinkTextColor(settings.getHighlightColor());
        root.setBackgroundColor(settings.getBackgroundColor());

        icons = new View[2];
        LayoutInflater inflater = LayoutInflater.from(this);
        icons[0] = inflater.inflate(R.layout.tab_tw, null);
        icons[1] = inflater.inflate(R.layout.tab_fa, null);

        FragmentAdapter adapter = new FragmentAdapter(getSupportFragmentManager(), AdapterType.PROFILE_TAB, userId, "");
        pager.setOffscreenPageLimit(2);
        pager.setAdapter(adapter);
        tab.setupWithViewPager(pager);
        tab.addOnTabSelectedListener(this);
        following.setOnClickListener(this);
        follower.setOnClickListener(this);

        for (int i = 0; i < icons.length; i++) {
            TabLayout.Tab t = tab.getTabAt(i);
            if (t != null)
                t.setCustomView(icons[i]);
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (profileAsync == null) {
            profileAsync = new ProfileLoader(this, Mode.LDR_PROFILE);
            profileAsync.execute(userId);
        }
    }


    @Override
    protected void onStop() {
        if (profileAsync != null && profileAsync.getStatus() == Status.RUNNING)
            profileAsync.cancel(true);
        super.onStop();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        getMenuInflater().inflate(R.menu.profile, m);
        if (!home) {
            m.findItem(R.id.profile_follow).setVisible(true);
            m.findItem(R.id.profile_block).setVisible(true);
            m.findItem(R.id.profile_mute).setVisible(true);
            m.findItem(R.id.profile_settings).setVisible(false);
        }
        return super.onCreateOptionsMenu(m);
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu m) {
        if (!home) {
            MenuItem followIcon = m.findItem(R.id.profile_follow);
            MenuItem blockIcon = m.findItem(R.id.profile_block);
            MenuItem muteIcon = m.findItem(R.id.profile_mute);
            MenuItem dmIcon = m.findItem(R.id.profile_message);

            if (isFriend) {
                followIcon.setIcon(R.drawable.follow_enabled);
                followIcon.setTitle(R.string.unfollow);
            } else if (requested) {
                followIcon.setIcon(R.drawable.follow_requested);
                followIcon.setTitle(R.string.follow_requested);
            } else {
                followIcon.setIcon(R.drawable.follow);
                followIcon.setTitle(R.string.follow);
            }
            if (isBlocked) {
                blockIcon.setTitle(R.string.unblock);
                followIcon.setVisible(false);
            } else {
                blockIcon.setTitle(R.string.block);
                followIcon.setVisible(true);
            }
            if (isMuted) {
                muteIcon.setTitle(R.string.unmute);
            } else {
                muteIcon.setTitle(R.string.mute);
            }
            if (!canDm) {
                dmIcon.setVisible(false);
            }
        }
        return super.onPrepareOptionsMenu(m);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (profileAsync != null && profileAsync.getStatus() != Status.RUNNING) {
            switch (item.getItemId()) {
                case R.id.profile_tweet:
                    Intent tweet = new Intent(this, TweetPopup.class);
                    if (!home)
                        tweet.putExtra("Addition", username);
                    startActivity(tweet);
                    break;

                case R.id.profile_follow:
                    profileAsync = new ProfileLoader(this, ProfileLoader.Mode.ACTION_FOLLOW);
                    if (!isFriend) {
                        profileAsync.execute(userId);
                    } else {
                        new Builder(this).setMessage(R.string.confirm_unfollow)
                                .setNegativeButton(R.string.no_confirm, null)
                                .setPositiveButton(R.string.yes_confirm, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        profileAsync.execute(userId);
                                    }
                                })
                                .show();
                    }
                    break;

                case R.id.profile_block:
                    profileAsync = new ProfileLoader(this, ProfileLoader.Mode.ACTION_BLOCK);
                    if (isBlocked) {
                        profileAsync.execute(userId);
                    } else {
                        new Builder(this).setMessage(R.string.confirm_block)
                                .setNegativeButton(R.string.no_confirm, null)
                                .setPositiveButton(R.string.yes_confirm, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        profileAsync.execute(userId);
                                    }
                                })
                                .show();
                    }
                    break;

                case R.id.profile_mute:
                    profileAsync = new ProfileLoader(this, ProfileLoader.Mode.ACTION_MUTE);
                    profileAsync.execute(userId);
                    break;

                case R.id.profile_message:
                    if (home) {
                        Intent dm = new Intent(this, DirectMessage.class);
                        startActivity(dm);
                    } else {
                        Intent sendDm = new Intent(this, MessagePopup.class);
                        sendDm.putExtra("username", username);
                        startActivity(sendDm);
                    }
                    break;

                case R.id.profile_settings:
                    Intent editProfile = new Intent(this, ProfileEdit.class);
                    startActivity(editProfile);
                    profileAsync = null;
                    break;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        if (tabIndex == 0) {
            super.onBackPressed();
        } else {
            pager.setCurrentItem(0);
        }
    }


    @Override
    public void onClick(String text) {
        Intent intent = new Intent(this, SearchPage.class);
        intent.putExtra("search", text);
        startActivity(intent);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.following:
                if (!isLocked) {
                    Intent following = new Intent(this, UserDetail.class);
                    following.putExtra("ID", userId);
                    following.putExtra("mode", UserDetail.UserType.FRIENDS);
                    startActivity(following);
                }
                break;

            case R.id.follower:
                if (!isLocked) {
                    Intent follower = new Intent(this, UserDetail.class);
                    follower.putExtra("ID", userId);
                    follower.putExtra("mode", UserDetail.UserType.FOLLOWERS);
                    startActivity(follower);
                }
                break;

            case R.id.links:
                ConnectivityManager mConnect = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (mConnect.getActiveNetworkInfo() != null && mConnect.getActiveNetworkInfo().isConnected()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    String link = lnkTxt.getText().toString();
                    intent.setData(Uri.parse(link));
                    startActivity(intent);
                } else {
                    Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }


    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        tabIndex = tab.getPosition();
    }


    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
    }


    @Override
    public void onTabReselected(TabLayout.Tab tab) {
    }


    public void setTweetCount(int tweets, int favors) {
        NumberFormat formatter = NumberFormat.getIntegerInstance();
        TextView tweetCount = icons[0].findViewById(R.id.profile_tweet_count);
        tweetCount.setText(formatter.format(tweets));
        TextView favorCount = icons[1].findViewById(R.id.profile_favor_count);
        favorCount.setText(formatter.format(favors));
    }


    public void setConnection(boolean isFriend, boolean isMuted, boolean isBlocked,
                              boolean isLocked, boolean canDm, boolean requested) {
        this.isFriend = isFriend;
        this.isMuted = isMuted;
        this.isBlocked = isBlocked;
        this.isLocked = isLocked;
        this.canDm = canDm;
        this.requested = requested;
    }
}