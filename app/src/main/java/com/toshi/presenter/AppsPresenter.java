/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.presenter;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Patterns;
import android.view.View;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.toshi.R;
import com.toshi.manager.AppsManager;
import com.toshi.manager.UserManager;
import com.toshi.model.local.Dapp;
import com.toshi.model.local.ToshiEntity;
import com.toshi.model.local.User;
import com.toshi.model.network.App;
import com.toshi.util.BrowseType;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.BrowseActivity;
import com.toshi.view.activity.ViewUserActivity;
import com.toshi.view.activity.WebViewActivity;
import com.toshi.view.adapter.HorizontalAdapter;
import com.toshi.view.adapter.ToshiEntityAdapter;
import com.toshi.view.adapter.listeners.OnItemClickListener;
import com.toshi.view.custom.HorizontalLineDivider;
import com.toshi.view.fragment.toplevel.AppsFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Single;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
import static com.toshi.util.BrowseType.VIEW_TYPE_LATEST_APPS;
import static com.toshi.util.BrowseType.VIEW_TYPE_LATEST_PUBLIC_USERS;
import static com.toshi.util.BrowseType.VIEW_TYPE_TOP_RATED_APPS;
import static com.toshi.util.BrowseType.VIEW_TYPE_TOP_RATED_PUBLIC_USERS;

public class AppsPresenter implements Presenter<AppsFragment>{

    private AppsFragment fragment;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttaching = true;

    private List<App> topRatedApps;
    private List<App> featuredApps;
    private List<User> topRatedUsers;
    private List<User> latestUsers;

    private int topRatedAppsScrollPosition = 0;
    private int featuredAppsScrollPosition = 0;
    private int topRatedUsersScrollPosition = 0;
    private int latestUsersScrollPosition = 0;

    @Override
    public void onViewAttached(AppsFragment view) {
        this.fragment = view;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }

        initView();
        tryRerunQuery();
        fetchData();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initView() {
        initClickListeners();
        initSearchAppsRecyclerView();
        iniTopRatedAppsRecycleView();
        initLatestAppsRecycleView();
        initTopRatedPublicUsersRecyclerView();
        initLatestPublicUsersRecyclerView();
        initSearchView();
    }

    private void tryRerunQuery() {
        if (!getSearchAdapter().isEmpty()) return;
        final String searchQuery = this.fragment.getBinding().search.getText().toString();
        if (searchQuery.length() > 0) {
            runSearchQuery(searchQuery);
        }
    }

    private void initClickListeners() {
        this.fragment.getBinding().moreTopRatedApps.setOnClickListener(__ -> startBrowseActivity(VIEW_TYPE_TOP_RATED_APPS));
        this.fragment.getBinding().moreLatestApps.setOnClickListener(__ -> startBrowseActivity(VIEW_TYPE_LATEST_APPS));
        this.fragment.getBinding().moreTopRatedPublicUsers.setOnClickListener(__ -> startBrowseActivity(VIEW_TYPE_TOP_RATED_PUBLIC_USERS));
        this.fragment.getBinding().moreLatestPublicUsers.setOnClickListener(__ -> startBrowseActivity(VIEW_TYPE_LATEST_PUBLIC_USERS));
        this.fragment.getBinding().clearButton.setOnClickListener(__ -> this.fragment.getBinding().search.setText(null));
    }

    private void startBrowseActivity(final @BrowseType.Type int viewType) {
        final Intent intent = new Intent(this.fragment.getActivity(), BrowseActivity.class)
                .putExtra(BrowseActivity.VIEW_TYPE, viewType);
        this.fragment.startActivity(intent);
    }

    private void initSearchAppsRecyclerView() {
        final RecyclerView searchAppList = this.fragment.getBinding().searchList;
        searchAppList.setLayoutManager(new LinearLayoutManager(this.fragment.getContext()));
        final ToshiEntityAdapter adapter = new ToshiEntityAdapter()
                .setOnItemClickListener(this::handleAppClicked)
                .setOnDappLaunchListener(this::handleDappLaunch);
        searchAppList.setAdapter(adapter);

        final int dividerLeftPadding = fragment.getResources().getDimensionPixelSize(R.dimen.avatar_size_small)
                + fragment.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin)
                + fragment.getResources().getDimensionPixelSize(R.dimen.list_item_avatar_margin);
        final int dividerRightPadding = fragment.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        final HorizontalLineDivider lineDivider =
                new HorizontalLineDivider(ContextCompat.getColor(this.fragment.getContext(), R.color.divider))
                        .setRightPadding(dividerRightPadding)
                        .setLeftPadding(dividerLeftPadding)
                        .setSkipFirst(true);
        searchAppList.addItemDecoration(lineDivider);
    }

    private void iniTopRatedAppsRecycleView() {
        initRecyclerView(
                this.fragment.getBinding().topRatedApps,
                new HorizontalAdapter<App>(5),
                this::handleAppClicked
        );
    }

    private void initLatestAppsRecycleView() {
        initRecyclerView(
                this.fragment.getBinding().featuredApps,
                new HorizontalAdapter<App>(4),
                this::handleAppClicked
        );
    }

    private void initTopRatedPublicUsersRecyclerView() {
        initRecyclerView(
                this.fragment.getBinding().topRatedPublicUsers,
                new HorizontalAdapter<User>(5),
                this::handleUserClicked
        );
    }

    private void initLatestPublicUsersRecyclerView() {
        initRecyclerView(
                this.fragment.getBinding().latestPublicUsers,
                new HorizontalAdapter<User>(6),
                this::handleUserClicked
        );
    }

    private void initRecyclerView(final RecyclerView recyclerView, final HorizontalAdapter adapter, final OnItemClickListener onItemClickListener) {
        recyclerView.setLayoutManager(new LinearLayoutManager(this.fragment.getContext(), LinearLayoutManager.HORIZONTAL, false));
        adapter.setOnItemClickListener(onItemClickListener);
        recyclerView.setAdapter(adapter);
        recyclerView.setNestedScrollingEnabled(false);
    }

    private void handleAppClicked(final Object elem) {
        if (this.fragment == null || elem == null) return;
        startProfileActivity(((ToshiEntity)elem).getToshiId());
    }

    private void handleUserClicked(final Object elem) {
        if (this.fragment == null) return;
        startProfileActivity(((ToshiEntity)elem).getToshiId());
    }

    private void startProfileActivity(final String userAddress) {
        final Intent intent = new Intent(this.fragment.getContext(), ViewUserActivity.class)
                .putExtra(ViewUserActivity.EXTRA__USER_ADDRESS, userAddress);
        this.fragment.getContext().startActivity(intent);
    }

    private void handleDappLaunch(final Dapp dapp) {
        final Intent intent = new Intent(this.fragment.getContext(), WebViewActivity.class)
                .putExtra(WebViewActivity.EXTRA__ADDRESS, dapp.getAddress());
        this.fragment.getContext().startActivity(intent);
    }

    private void initSearchView() {
        final Subscription searchSub =
                RxTextView.textChanges(this.fragment.getBinding().search)
                .skip(1)
                .debounce(400, TimeUnit.MILLISECONDS)
                .map(CharSequence::toString)
                .subscribe(
                        this::runSearchQuery,
                        throwable -> LogUtil.exception(getClass(), throwable)
                );

        final Subscription enterSub =
                RxTextView.editorActions(this.fragment.getBinding().search)
                .filter(event -> event == IME_ACTION_DONE)
                .toCompletable()
                .subscribe(
                        this::handleSearchPressed,
                        throwable -> LogUtil.e(getClass(), throwable.toString())
                );

        updateViewState();

        this.subscriptions.addAll(searchSub, enterSub);
    }

    private void runSearchQuery(final String query) {
        final Subscription sub =
            Single.just(query)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess(__ -> updateViewState())
                .doOnSuccess(this::tryRenderDappLink)
                .flatMap(this::searchOnlineByUsername)
                .map(users -> new ArrayList<ToshiEntity>(users))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        toshiEntities -> getSearchAdapter().addItems(toshiEntities),
                        throwable -> LogUtil.exception(getClass(), "Error while searching for app", throwable)
                );

        this.subscriptions.add(sub);
    }

    private void handleSearchPressed() {
        if (getSearchAdapter() == null || getSearchAdapter().getNumberOfApps() != 1) return;
        final ToshiEntity appToLaunch = getSearchAdapter().getFirstApp();
        if (appToLaunch instanceof Dapp) {
            this.handleDappLaunch((Dapp) appToLaunch);
        }
    }

    private Single<List<User>> searchOnlineByUsername(final String query) {
        return BaseApplication
                .get()
                .getRecipientManager()
                .searchOnlineUsers(query);
    }

    private void updateViewState() {
        final boolean shouldShowSearchResult = this.fragment.getBinding().search.getText().toString().length() > 0;

        if (shouldShowSearchResult) {
            this.fragment.getBinding().searchList.setVisibility(View.VISIBLE);
            this.fragment.getBinding().clearButton.setVisibility(View.VISIBLE);
            this.fragment.getBinding().scrollView.setVisibility(View.GONE);
        } else {
            this.fragment.getBinding().searchList.setVisibility(View.GONE);
            this.fragment.getBinding().clearButton.setVisibility(View.GONE);
            this.fragment.getBinding().scrollView.setVisibility(View.VISIBLE);
        }
    }

    private void tryRenderDappLink(final String searchString) {
        if (!Patterns.WEB_URL.matcher(searchString.trim()).matches()) {
            getSearchAdapter().removeDapp();
            return;
        }

        getSearchAdapter().addDapp(searchString);
    }

    private ToshiEntityAdapter getSearchAdapter() {
        return (ToshiEntityAdapter) this.fragment.getBinding().searchList.getAdapter();
    }

    private void fetchData() {
        fetchTopRatedApps();
        fetchFeaturedApps();
        fetchTopRatedPublicUsers();
        fetchLatestPublicUsers();
    }

    private void fetchTopRatedApps() {
        if (this.topRatedApps != null && this.topRatedApps.size() > 0) {
            handleTopRatedApps(this.topRatedApps);
            scrollToRetainedPosition(
                    this.fragment.getBinding().topRatedApps,
                    this.topRatedAppsScrollPosition
            );
            return;
        }

        final Subscription sub =
                getAppManager()
                .getTopRatedApps(10)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleTopRatedApps,
                        throwable -> LogUtil.exception(getClass(), "Error while fetching top rated apps", throwable)
                );

        this.subscriptions.add(sub);
    }

    private void handleTopRatedApps(final List<App> apps) {
        final HorizontalAdapter<App> adapter = (HorizontalAdapter) this.fragment.getBinding().topRatedApps.getAdapter();
        adapter.setItems(apps);
        this.topRatedApps = apps;
    }

    private void fetchFeaturedApps() {
        if (this.featuredApps != null && this.featuredApps.size() > 0) {
            handleFeaturedApps(this.featuredApps);
            scrollToRetainedPosition(
                    this.fragment.getBinding().featuredApps,
                    this.featuredAppsScrollPosition
            );
            return;
        }

        final Subscription sub =
                getAppManager()
                .getLatestApps(10)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleFeaturedApps,
                        throwable -> LogUtil.exception(getClass(), "Error while fetching top rated apps", throwable)
                );

        this.subscriptions.add(sub);
    }

    private AppsManager getAppManager() {
        return BaseApplication
                .get()
                .getToshiManager()
                .getAppsManager();
    }

    private void handleFeaturedApps(final List<App> apps) {
        final HorizontalAdapter<App> adapter = (HorizontalAdapter) this.fragment.getBinding().featuredApps.getAdapter();
        adapter.setItems(apps);
        this.featuredApps = apps;

    }

    private void fetchTopRatedPublicUsers() {
        if (this.topRatedUsers != null && this.topRatedUsers.size() > 0) {
            handleTopRatedPublicUser(this.topRatedUsers);
            scrollToRetainedPosition(
                    this.fragment.getBinding().topRatedPublicUsers,
                    this.topRatedUsersScrollPosition
            );
            return;
        }

        final Subscription sub =
                getUserManager()
                .getTopRatedPublicUsers(10)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleTopRatedPublicUser,
                        throwable -> LogUtil.exception(getClass(), "Error while fetching public users", throwable)
                );

        this.subscriptions.add(sub);
    }

    private void handleTopRatedPublicUser(final List<User> users) {
        final HorizontalAdapter<User> adapter = (HorizontalAdapter) this.fragment.getBinding().topRatedPublicUsers.getAdapter();
        adapter.setItems(users);
        this.topRatedUsers = users;
    }

    private void fetchLatestPublicUsers() {
        if (this.latestUsers != null && this.latestUsers.size() > 0) {
            handleLatestPublicUser(this.latestUsers);
            scrollToRetainedPosition(
                    this.fragment.getBinding().latestPublicUsers,
                    this.latestUsersScrollPosition
            );
            return;
        }

        final Subscription sub =
                getUserManager()
                .getLatestPublicUsers(10)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleLatestPublicUser,
                        throwable -> LogUtil.exception(getClass(), "Error while fetching public users", throwable)
                );

        this.subscriptions.add(sub);
    }

    private void handleLatestPublicUser(final List<User> users) {
        final HorizontalAdapter<User> adapter = (HorizontalAdapter) this.fragment.getBinding().latestPublicUsers.getAdapter();
        adapter.setItems(users);
        this.latestUsers = users;
    }

    private UserManager getUserManager() {
        return BaseApplication
                .get()
                .getToshiManager()
                .getUserManager();
    }

    private void scrollToRetainedPosition(final RecyclerView recyclerView, final int position) {
        recyclerView.getLayoutManager().scrollToPosition(position);
    }

    @Override
    public void onViewDetached() {
        setScrollState();
        this.subscriptions.clear();
        this.fragment = null;
    }

    private void setScrollState() {
        final LinearLayoutManager topRatedAppsLayoutManager = (LinearLayoutManager) this.fragment.getBinding().topRatedApps.getLayoutManager();
        this.topRatedAppsScrollPosition = topRatedAppsLayoutManager.findFirstCompletelyVisibleItemPosition();
        final LinearLayoutManager featuredAppsLayoutManager = (LinearLayoutManager) this.fragment.getBinding().featuredApps.getLayoutManager();
        this.featuredAppsScrollPosition = featuredAppsLayoutManager.findFirstCompletelyVisibleItemPosition();
        final LinearLayoutManager topRatedYsersLayoutManager = (LinearLayoutManager) this.fragment.getBinding().topRatedPublicUsers.getLayoutManager();
        this.topRatedUsersScrollPosition = topRatedYsersLayoutManager.findFirstCompletelyVisibleItemPosition();
        final LinearLayoutManager featuredUsersLayoutManager = (LinearLayoutManager) this.fragment.getBinding().latestPublicUsers.getLayoutManager();
        this.latestUsersScrollPosition = featuredUsersLayoutManager.findFirstCompletelyVisibleItemPosition();
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
        this.fragment = null;
    }
}
