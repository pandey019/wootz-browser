// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/ntp/feed_management/follow_management_mediator.h"

#import "base/check.h"
#import "base/memory/raw_ptr.h"
#import "ios/chrome/browser/favicon/model/favicon_loader.h"
#import "ios/chrome/browser/favicon/model/ios_chrome_favicon_loader_factory.h"
#import "ios/chrome/browser/follow/model/follow_browser_agent.h"
#import "ios/chrome/browser/follow/model/follow_browser_agent_observer_bridge.h"
#import "ios/chrome/browser/follow/model/follow_browser_agent_observing.h"
#import "ios/chrome/browser/net/model/crurl.h"
#import "ios/chrome/browser/ui/follow/followed_web_channel.h"
#import "ios/chrome/browser/ui/ntp/feed_management/follow_management_follow_delegate.h"
#import "ios/chrome/browser/ui/ntp/feed_management/follow_management_ui_updater.h"
#import "ios/chrome/common/ui/favicon/favicon_constants.h"

namespace {

// Converts a FollowedWebSite to a FollowedWebChannel.
FollowedWebChannel* FollowedWebSiteToFollowedWebChannel(
    FollowedWebSite* web_site) {
  FollowedWebChannel* web_channel = [[FollowedWebChannel alloc] init];
  web_channel.title = web_site.title;
  web_channel.webPageURL = [[CrURL alloc] initWithNSURL:web_site.webPageURL];
  web_channel.faviconURL = [[CrURL alloc] initWithNSURL:web_site.faviconURL];
  web_channel.rssURL = [[CrURL alloc] initWithNSURL:web_site.RSSURL];
  web_channel.state = web_site.state;
  return web_channel;
}

}  // anonymous namespace

@interface FollowManagementMediator () <FollowBrowserAgentObserving>
@end

@implementation FollowManagementMediator {
  // FaviconLoader retrieves favicons for a given page URL.
  raw_ptr<FaviconLoader> _faviconLoader;

  // FollowBrowserAgent retrieves the list of followed channels.
  raw_ptr<FollowBrowserAgent> _followBrowserAgent;

  // Used to observer FollowBrowserAgent.
  std::unique_ptr<FollowServiceObserver> _observer;

  // List of registered FollowManagementUIUpdaters.
  NSMutableArray<id<FollowManagementUIUpdater>>* _updaters;
}

- (instancetype)initWithBrowserAgent:(FollowBrowserAgent*)browserAgent
                       faviconLoader:(FaviconLoader*)faviconLoader {
  self = [super init];
  if (self) {
    _followBrowserAgent = browserAgent;
    _observer = std::make_unique<FollowBrowserAgentObserverBridge>(
        self, _followBrowserAgent);
    _faviconLoader = faviconLoader;
    _updaters = [[NSMutableArray alloc] init];
  }
  return self;
}

- (void)addFollowManagementUIUpdater:(id<FollowManagementUIUpdater>)updater {
  [_updaters addObject:updater];
}

- (void)removeFollowManagementUIUpdater:(id<FollowManagementUIUpdater>)updater {
  [_updaters removeObject:updater];
}

- (void)detach {
  _observer.reset();
  _followBrowserAgent = nullptr;
  _faviconLoader = nullptr;
}

- (void)dealloc {
  DCHECK(!_followBrowserAgent) << "-detach must be called before -dealloc";
}

#pragma mark - FollowedWebChannelsDataSource

- (NSArray<FollowedWebChannel*>*)followedWebChannels {
  NSMutableArray<FollowedWebChannel*>* channels = [[NSMutableArray alloc] init];
  for (FollowedWebSite* webSite in _followBrowserAgent->GetFollowedWebSites()) {
    [channels addObject:FollowedWebSiteToFollowedWebChannel(webSite)];
  }
  return channels;
}

- (void)loadFollowedWebSites {
  _followBrowserAgent->LoadFollowedWebSites();
}

#pragma mark - FollowManagementFollowDelegate

- (void)unfollowFollowedWebChannel:(FollowedWebChannel*)followedWebChannel {
  NSArray<NSURL*>* RSSURLs = nil;
  if (followedWebChannel.rssURL) {
    NSURL* RSSUL = followedWebChannel.rssURL.nsurl;
    RSSURLs = RSSUL ? @[ RSSUL ] : nil;
  }

  WebPageURLs* webPageURLs =
      [[WebPageURLs alloc] initWithURL:followedWebChannel.webPageURL.nsurl
                               RSSURLs:RSSURLs];

  _followBrowserAgent->UnfollowWebSite(webPageURLs, FollowSource::Management);
}

#pragma mark - TableViewFaviconDataSource

- (void)faviconForPageURL:(CrURL*)URL
               completion:(void (^)(FaviconAttributes*))completion {
  _faviconLoader->FaviconForPageUrl(
      URL.gurl, kDesiredSmallFaviconSizePt, kMinFaviconSizePt,
      /*fallback_to_google_server=*/true, ^(FaviconAttributes* attributes) {
        completion(attributes);
      });
}

#pragma mark - FollowBrowserAgentObserving

- (void)followedWebSite:(FollowedWebSite*)followedWebSite {
  FollowedWebChannel* followedWebChannel =
      FollowedWebSiteToFollowedWebChannel(followedWebSite);

  for (id<FollowManagementUIUpdater> updater in _updaters) {
    [updater addFollowedWebChannel:followedWebChannel];
  }
}

- (void)unfollowedWebSite:(FollowedWebSite*)followedWebSite {
  FollowedWebChannel* followedWebChannel =
      FollowedWebSiteToFollowedWebChannel(followedWebSite);

  for (id<FollowManagementUIUpdater> updater in _updaters) {
    [updater removeFollowedWebChannel:followedWebChannel];
  }
}

- (void)followedWebSitesLoaded {
  for (id<FollowManagementUIUpdater> updater in _updaters) {
    [updater updateFollowedWebSites];
  }
}

@end
