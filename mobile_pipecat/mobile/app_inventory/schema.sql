-- App Inventory tables
-- Run this SQL script to create the app inventory tables in your PostgreSQL database

-- Store category crawling cache
CREATE TABLE IF NOT EXISTS store_app_category_cache (
    package_name    VARCHAR(255)  NOT NULL,
    store           VARCHAR(20)   NOT NULL DEFAULT 'google_play',
    store_category  VARCHAR(64),
    mapped_category VARCHAR(32),
    fetched_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (package_name, store)
);

-- Manual category override (always takes priority over store mapping)
CREATE TABLE IF NOT EXISTS app_category_override (
    package_name    VARCHAR(255) NOT NULL PRIMARY KEY,
    mapped_category VARCHAR(32)  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Navigation app identification table
-- Present → navigation, absent → maps (within MAPS_AND_NAVIGATION)
CREATE TABLE IF NOT EXISTS navigation_app_list (
    package_name VARCHAR(255) NOT NULL PRIMARY KEY,
    label        VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Store category → our category mapping rules
CREATE TABLE IF NOT EXISTS store_category_mapping (
    store           VARCHAR(20)  NOT NULL,
    store_category  VARCHAR(64)  NOT NULL,
    mapped_category VARCHAR(32)  NOT NULL,
    PRIMARY KEY (store, store_category)
);

-- App popularity counting (crowdsourced)
CREATE TABLE IF NOT EXISTS app_popularity_counter (
    package_name  VARCHAR(255)     NOT NULL PRIMARY KEY,
    device_count  BIGINT           NOT NULL DEFAULT 0,
    total_score   DOUBLE PRECISION NOT NULL DEFAULT 0
);

-- device_id dedup for popularity counting (one count per device)
CREATE TABLE IF NOT EXISTS app_popularity_device_log (
    device_id   VARCHAR(64)  NOT NULL PRIMARY KEY,
    counted_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Snapshot (user app inventory history, append-only)
CREATE TABLE IF NOT EXISTS user_app_inventory_snapshot (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       VARCHAR(128) NOT NULL,
    device_id     VARCHAR(64)  NOT NULL,
    platform      VARCHAR(10)  NOT NULL DEFAULT 'android',
    client_sorted BOOLEAN      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    client_ts     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_snapshot_user_created
    ON user_app_inventory_snapshot (user_id, created_at DESC);

-- Snapshot items (normalized, only top N per category stored)
CREATE TABLE IF NOT EXISTS user_app_inventory_item (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id   UUID         NOT NULL REFERENCES user_app_inventory_snapshot(id) ON DELETE CASCADE,
    package_name  VARCHAR(255) NOT NULL,
    label         VARCHAR(255),
    category      VARCHAR(32)  NOT NULL,
    usage_score   DOUBLE PRECISION,
    sort_order    INTEGER      NOT NULL,
    is_system     BOOLEAN      DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_item_snapshot_category
    ON user_app_inventory_item (snapshot_id, category);


-- ============================================================
-- Seed data: store_category_mapping (Google Play → our enum)
-- ============================================================
INSERT INTO store_category_mapping (store, store_category, mapped_category) VALUES
    ('google_play', 'COMMUNICATION',       'social_chat'),
    ('google_play', 'SOCIAL',              'social_open'),
    ('google_play', 'MAPS_AND_NAVIGATION', 'maps'),
    ('google_play', 'GAME_ACTION',         'game'),
    ('google_play', 'GAME_ADVENTURE',      'game'),
    ('google_play', 'GAME_ARCADE',         'game'),
    ('google_play', 'GAME_BOARD',          'game'),
    ('google_play', 'GAME_CARD',           'game'),
    ('google_play', 'GAME_CASINO',         'game'),
    ('google_play', 'GAME_CASUAL',         'game'),
    ('google_play', 'GAME_EDUCATIONAL',    'game'),
    ('google_play', 'GAME_MUSIC',          'game'),
    ('google_play', 'GAME_PUZZLE',         'game'),
    ('google_play', 'GAME_RACING',         'game'),
    ('google_play', 'GAME_ROLE_PLAYING',   'game'),
    ('google_play', 'GAME_SIMULATION',     'game'),
    ('google_play', 'GAME_SPORTS',         'game'),
    ('google_play', 'GAME_STRATEGY',       'game'),
    ('google_play', 'GAME_TRIVIA',         'game'),
    ('google_play', 'GAME_WORD',           'game'),
    ('google_play', 'ENTERTAINMENT',       'entertainment'),
    ('google_play', 'PRODUCTIVITY',        'productivity'),
    ('google_play', 'LIFESTYLE',           'lifestyle'),
    ('google_play', 'EDUCATION',           'education'),
    ('google_play', 'FINANCE',             'finance'),
    ('google_play', 'SHOPPING',            'shopping'),
    ('google_play', 'HEALTH_AND_FITNESS',  'health_fitness'),
    ('google_play', 'TRAVEL_AND_LOCAL',    'travel_local'),
    ('google_play', 'FOOD_AND_DRINK',      'food_drink'),
    ('google_play', 'PHOTOGRAPHY',         'photo_video'),
    ('google_play', 'VIDEO_PLAYERS',       'photo_video'),
    ('google_play', 'MUSIC_AND_AUDIO',     'music_audio'),
    ('google_play', 'NEWS_AND_MAGAZINES',  'news'),
    ('google_play', 'BOOKS_AND_REFERENCE', 'books_reference'),
    ('google_play', 'BUSINESS',            'business'),
    ('google_play', 'TOOLS',               'utilities'),
    ('google_play', 'WEATHER',             'weather'),
    ('google_play', 'AUTO_AND_VEHICLES',   'travel_local'),
    ('google_play', 'HOUSE_AND_HOME',      'lifestyle'),
    ('google_play', 'PARENTING',           'lifestyle'),
    ('google_play', 'BEAUTY',              'lifestyle'),
    ('google_play', 'DATING',              'social_open'),
    ('google_play', 'EVENTS',              'lifestyle'),
    ('google_play', 'ART_AND_DESIGN',      'lifestyle'),
    ('google_play', 'COMICS',              'entertainment'),
    ('google_play', 'LIBRARIES_AND_DEMO',  'other'),
    ('google_play', 'PERSONALIZATION',     'utilities'),
    ('google_play', 'MEDICAL',             'health_fitness')
ON CONFLICT DO NOTHING;


-- ============================================================
-- Seed data: app_category_override (major well-known apps)
-- ============================================================
INSERT INTO app_category_override (package_name, mapped_category) VALUES
    -- social_chat (messaging)
    ('com.kakao.talk',                      'social_chat'),
    ('com.facebook.orca',                   'social_chat'),
    ('com.whatsapp',                        'social_chat'),
    ('org.telegram.messenger',              'social_chat'),
    ('com.discord',                         'social_chat'),
    ('jp.naver.line.android',               'social_chat'),
    ('com.samsung.android.messaging',       'social_chat'),
    ('com.google.android.apps.messaging',   'social_chat'),
    ('com.viber.voip',                      'social_chat'),
    ('com.Slack',                           'social_chat'),
    -- social_open (public feeds)
    ('com.facebook.katana',                 'social_open'),
    ('com.instagram.android',               'social_open'),
    ('com.twitter.android',                 'social_open'),
    ('com.zhiliaoapp.musically',            'social_open'),
    ('com.nhn.android.band',                'social_open'),
    ('com.snapchat.android',                'social_open'),
    ('com.reddit.frontpage',                'social_open'),
    ('com.linkedin.android',                'social_open'),
    ('com.pinterest',                       'social_open'),
    ('com.kakao.story',                     'social_open'),
    -- navigation
    ('com.locnall.KimGiSa',                'navigation'),
    ('com.skt.tmap.ku',                     'navigation'),
    ('com.nhn.android.nmap',                'navigation'),
    ('com.waze',                            'navigation'),
    ('com.skt.skaf.l001mtm091',             'navigation'),
    ('com.mnav.tmap',                       'navigation'),
    -- maps
    ('com.google.android.apps.maps',        'maps'),
    ('com.kakao.map',                       'maps'),
    -- entertainment
    ('com.google.android.youtube',          'entertainment'),
    ('com.netflix.mediaclient',             'entertainment'),
    ('com.disney.disneyplus',               'entertainment'),
    ('com.amazon.avod.thirdpartyclient',    'entertainment'),
    ('com.coupang.coupangplay',             'entertainment'),
    ('com.tving.tving',                     'entertainment'),
    ('com.wavve.wavve',                     'entertainment'),
    -- music_audio
    ('com.spotify.music',                   'music_audio'),
    ('com.apple.android.music',             'music_audio'),
    ('com.iloen.melon',                     'music_audio'),
    ('com.nhn.android.vibe',                'music_audio'),
    -- shopping
    ('com.coupang.mobile',                  'shopping'),
    ('com.ebay.kr.gmarket',                'shopping'),
    ('com.nhn.android.search',              'shopping'),
    ('com.lotteon.appclient',               'shopping'),
    -- finance
    ('com.kakaobank.channel',               'finance'),
    ('com.kbstar.kbbank',                   'finance'),
    ('com.shinhan.sbanking',                'finance'),
    ('com.wooribank.smart.npib',            'finance'),
    ('viva.republica.toss',                 'finance'),
    ('com.nhn.android.nhnpay',              'finance'),
    ('com.kakaopay.app',                    'finance'),
    -- food_drink
    ('com.baemin',                          'food_drink'),
    ('com.fineapp.yogiyo',                  'food_drink'),
    ('com.coupang.coupangeats',             'food_drink'),
    ('com.starbucks.co',                    'food_drink')
ON CONFLICT DO NOTHING;


-- ============================================================
-- Seed data: navigation_app_list
-- ============================================================
INSERT INTO navigation_app_list (package_name, label) VALUES
    ('com.locnall.KimGiSa',    '카카오내비'),
    ('com.skt.tmap.ku',         'T맵'),
    ('com.nhn.android.nmap',    '네이버 지도'),
    ('com.waze',                'Waze'),
    ('com.skt.skaf.l001mtm091', 'T맵 택시'),
    ('com.mnav.tmap',           'T맵 내비게이션')
ON CONFLICT DO NOTHING;
