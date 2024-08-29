![# botify](https://raw.githubusercontent.com/robinfriedli/aiode/master/resources-public/img/aiode-logo-wide.png)
 Discord bot that plays Spotify tracks and YouTube videos or any URL including Soundcloud links and Twitch streams.

Help keep aiode free and open source for everyone

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/R5R0XAC5J)

* Play and search Spotify tracks and YouTube videos or playlists or play any URL including Soundcloud links and Twitch streams
* Create cross-platform playlists with tracks from any source
* Simple and customisable player commands
* Create custom command presets as shortcuts for your most used commands
* Adjustable properties for even deeper customisation
* Sign in to Spotify to play your own playlists or upload aiode playlists
* Manage what roles can access which commands
* Customise how you want to summon your bot by using a custom prefix or giving your bot a name
* Advanced admin commands such as updating and rebooting the bot or cleaning up the database available to bot administrators
* Capable scripting sandbox that enables running and storing custom groovy scripts and modifying command behavior through interceptors

## Invite it to your guild

[Invite the bot to join your guild](https://discordapp.com/api/oauth2/authorize?client_id=483377420494176258&permissions=70315072&scope=bot)

## Host it yourself

### 1. Create a Discord app

#### 1.1 Go to https://discordapp.com/developers/applications and create an application
#### 1.2 Click "Bot" on the side menu to create a bot and copy the token for later

### 2. Create a Spotify app

#### 2.1 Go to https://developer.spotify.com/dashboard/applications to create a Spotify application and copy the client id
#### 2.2 Click on "Edit Settings" and whitelist your Redirect URI for the Spotify login
Don't have a domain? You could either go without logins all together and still use most of aiode's features or use your
router's public ip and setup port forwarding for your router.

### 3. Create a YouTube Data API project
#### 3.1 Go to https://console.developers.google.com/ and create a project for the YouTube Data API and create and copy the credentials

### 4. Setup aiode settings
#### 4.1 Apply your confidentials created in the previous steps and manage your private settings
##### 4.1.1 Navigate to your cloned project and go to `src/main/resources` and create the `settings-private.properties` from the example below and fill in the blanks. This file is included in gitignore to make sure you don't accidentally publish it.
##### 4.1.2 Adjust datasource properties and enter the database user and password, database setup will be discussed further in 4.2.1.
##### 4.1.3 To take advantage of the admin commands that can perform administrative actions, such as updating and restarting the bot, be sure to add your Discord user id to the `aiode.security.admin_users` property. To find your Discord user id, enable Developer Mode in the App Settings > Appearance. Then go to any guild, right click your user and click "Copy ID".
##### 4.1.4 To supplement [filebroker.io](https://github.com/filebroker) integration, you may set up a bot account and paste the username and password below. This ensures that the bot has access to all posts shared with that bot account.
##### 4.1.5 Set up YouTube bot detection countermeasures if you are getting the "sign in to confirm you are not a bot" error (optional)
##### 4.1.5.1 Enable YouTube OAUTH support for lavaplayer to avoid YouTube bot detection (causing the "sing in to confirm you are not a bot" error). Set value of `aiode.tokens.yt-oauth-refresh-token` to "init" and follow [the oauth flow](https://github.com/lavalink-devs/youtube-source?tab=readme-ov-file#using-oauth-tokens), then replace the value with your token.
##### 4.1.5.2 Set up a poToken by following [the guide](https://github.com/lavalink-devs/youtube-source?tab=readme-ov-file#using-a-potoken) and setting the `aiode.tokens.yt-po-token` and `aiode.tokens.yt-po-visitor-data` properties.
```properties
##########
# tokens #
##########
aiode.tokens.discord_token=
aiode.tokens.spotify_client_id=
aiode.tokens.spotify_client_secret=
aiode.tokens.youtube_credentials=
############
# security #
############
#define user ids (comma separated) that may access admin commands. These users can always use each command irregardless of access configurations
aiode.security.admin_users=
##############
# datasource #
##############
spring.datasource.username=postgres
spring.datasource.password=postgres
########
# IPv6 #
########
# list IPv6 blocks to use for the lavaplayer route planner (comma separated)
aiode.preferences.ipv6_blocks=
##############################
# top.gg settings (optional) #
##############################
#copy your discord client id here
aiode.tokens.discord_bot_id=
#copy your top.gg token here
aiode.tokens.topgg_token=
#######################
# youtube credentials #
#######################
# set these properties to support age restricted videos on YouTube, see https://github.com/Walkyst/lavaplayer-fork/issues/18
aiode.tokens.yt-email=
aiode.tokens.yt-password=
aiode.tokens.yt-oauth-refresh-token=
aiode.tokens.yt-po-token=
aiode.tokens.yt-po-visitor-data=
##############
# filebroker #
##############
aiode.filebroker.bot_user_name=
aiode.filebroker.bot_user_password=
```
#### 4.2 Adjust application.properties
##### 4.2.1 Review the datasource properties and make necessary adjustments. If you are using a local postgres server and name your database "aiode" you can leave it as it is. If you need help setting up your postgres server, please refer to their official documentation: http://www.postgresqltutorial.com/.
##### 4.2.2 For Aiode to manage the YouTube API quota usage automatically, be sure to fill in the `aiode.preferences.youtube_api_daily_quota` property; open the Google developer console and go to Library > YouTube Data API v3 > Manage > Quotas
##### 4.2.3 Change the `aiode.server.base_uri` property to your domain or public IP (without slash at the end) and adjust `aiode.server.spotify_login_callback` to the corresponding endpoint for Spotify logins (normally BASE_URI + "/login")
Don't have a domain? You could either go without a web server all together and still use most of aiode's features or use your
router's public ip and setup port forwarding for your router to the machine where you're running aiode via the port specified by the `SERVER_PORT` property.
```properties
###################
# server settings #
###################
aiode.server.port=8000
aiode.server.base_uri=http://localhost:8000
aiode.server.spotify_login_callback=http://localhost:8000/login
spring.liquibase.change-log=classpath:liquibase/dbchangelog.xml
spring.liquibase.contexts=definition,initialvalue,constraint
liquibase.change-log-path=src/main/resources/liquibase/dbchangelog.xml
liquibase.referenceUrl=hibernate:spring:net.robinfriedli.aiode.entities?dialect=org.hibernate.dialect.PostgreSQLDialect
###############
# preferences #
###############
# replace this value with your YouTube API Quota: open the Google developer console and go to Library > YouTube Data API v3 > Manage > Quotas
aiode.preferences.youtube_api_daily_quota=1000001
# partitioned = true means that data, such as playlists, presets and scripts will be separated between guilds
# if you host this bot privately and want to share data between few guilds you can set this property to 'false'
# however you should decide decide which mode to use before using the bot; if you have been using the bot with partitioned = true
# and several guilds have playlists or presets or scripts with the same name and you switch to partitioned = false then
# many queries will break because names are no longer unique
aiode.preferences.mode_partitioned=true
aiode.preferences.queue_size_max=10000
# maximum entity count per guild (if mode_partitioned = true, else entity count total)
aiode.preferences.playlist_count_max=50
aiode.preferences.playlist_size_max=5000
aiode.preferences.preset_count_max=100
aiode.preferences.script_count_max=100
aiode.preferences.interceptor_count_max=10
# defines max heap size for the bootRun task
aiode.preferences.max_heap_size=2048m
# disable / enable commands in the scripting category and custom scripted command interceptors
aiode.preferences.enable_scripting=true
##############
# datasource #
##############
spring.datasource.url=jdbc:postgresql://localhost:5432/aiode
spring.datasource.driverClassName=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.current_session_context_class=thread
# pool
spring.datasource.hikari.minimumIdle=5
spring.datasource.hikari.maximumPoolSize=50
# cache
spring.jpa.properties.hibernate.cache.use_query_cache=true
spring.jpa.properties.hibernate.cache.use_second_level_cache=true
spring.jpa.properties.hibernate.cache.region.factory_class=org.hibernate.cache.jcache.JCacheRegionFactory
spring.jpa.properties.hibernate.javax.cache.provider=org.ehcache.jsr107.EhcacheCachingProvider
spring.jpa.properties.hibernate.javax.cache.missing_cache_strategy=create
##############
# filebroker #
##############
aiode.filebroker.api_base_url=https://filebroker.io/api/
```


### 6 Compile and run aiode
Requires:
* java jdk 21 or above (preferably 21, as it is the version used in development and thus main supported version)
* (only for the experimental webapp) rust and cargo-make with the `wasm32-unknown-unknown` target for the webapp

#### 6.1 Compile webapp (experimental only, skip for normal installation)
Install rust, preferably via rustup, then add the `wasm32-unknown-unknown` target by running `rustup target add wasm32-unknown-unknown` and install cargo-make with `cargo install --force cargo-make`. Finally, navigate to `src/main/webapp` and compile the webapp with `cargo make build`.

#### 6.2 Compile bot
Navigate to the project root directory and install aiode by running `./gradlew build` (or if you have gradle installed you can just run `gradle build`).

#### 6.3 Run aiode
Then you can launch aiode using the jar file or the bootRun gradle task. You can either run the
jar file in `build/libs` by running `java -jar build/libs/aiode-1.0-SNAPSHOT.jar` or run the bash script `bash/launch.sh`
or use the gradle bootRun task by running `./gradlew bootRun`. To keep the program running when closing the terminal window use a
terminal multiplexer tool like tmux (Unix-like operating systems (e.g. Linux or MacOS) only) to manage a terminal session.
