![# botify](https://raw.githubusercontent.com/robinfriedli/botify/master/resources-public/img/botify-logo-wide.png)
 Discord bot that plays Spotify tracks and YouTube videos or any URL including Soundcloud links and Twitch streams.

* Play and search Spotify tracks and YouTube videos or playlists or play any URL including Soundcloud links and Twitch streams
* Create cross-platform playlists with tracks from any source
* Simple and customisable player commands
* Create custom command presets as shortcuts for your most used commands
* Adjustable properties for even deeper customisation
* Sign in to Spotify to play your own playlists or upload botify playlists
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
Don't have a domain? You could either go without logins all together and still use most of botify's features or use your
router's public ip and setup port forwarding for your router.

### 3. Create a YouTube Data API project
#### 3.1 Go to https://console.developers.google.com/ and create a project for the YouTube Data API and create and copy the credentials

### 4. Setup botify settings
#### 4.1 Apply your confidentials created in the previous steps and manage your private settings
##### 4.1.1 Navigate to your cloned project and go to `src/main/resources` and create the `settings-private.properties` from the example below and fill in the blanks. This file is included in gitignore to make sure you don't accidentally publish it.
##### 4.1.2 Adjust datasource properties and enter the database user and password, database setup will be discussed further in 4.2.1.
##### 4.1.3 To take advantage of the admin commands that can perform administrative actions, such as updating and restarting the bot, be sure to add your Discord user id to the `botify.security.admin_users` property. To find your Discord user id, enable Developer Mode in the App Settings > Appearance. Then go to any guild, right click your user and click "Copy ID".
```properties
##########
# tokens #
##########
botify.tokens.discord_token=
botify.tokens.spotify_client_id=
botify.tokens.spotify_client_secret=
botify.tokens.youtube_credentials=
############
# security #
############
#define user ids (comma separated) that may access admin commands. These users can always use each command irregardless of access configurations
botify.security.admin_users=
##############
# datasource #
##############
spring.datasource.username=postgres
spring.datasource.password=postgres
########
# IPv6 #
########
# list IPv6 blocks to use for the lavaplayer route planner (comma separated)
botify.preferences.ipv6_blocks=
##############################
# top.gg settings (optional) #
##############################
#copy your discord client id here
botify.tokens.discord_bot_id=
#copy your top.gg token here
botify.tokens.topgg_token=
```
#### 4.2 Adjust application.properties
##### 4.2.1 Review the datasource properties and make necessary adjustments. If you are using a local postgres server and name your database "botify" you can leave it as it is. If you need help setting up your postgres server, please refer to their official documentation: http://www.postgresqltutorial.com/.
##### 4.2.2 For Botify to manage the YouTube API quota usage automatically, be sure to fill in the `botify.preferences.youtube_api_daily_quota` property; open the Google developer console and go to Library > YouTube Data API v3 > Manage > Quotas
##### 4.2.3 Change the `botify.server.base_uri` property to your domain or public IP (without slash at the end) and adjust `botify.server.spotify_login_callback` to the corresponding endpoint for Spotify logins (normally BASE_URI + "/login")
Don't have a domain? You could either go without a web server all together and still use most of botify's features or use your
router's public ip and setup port forwarding for your router to the machine where you're running botify via the port specified by the `SERVER_PORT` property.
```properties
###################
# server settings #
###################
botify.server.port=8000
botify.server.base_uri=http://localhost:8000
botify.server.spotify_login_callback=http://localhost:8000/login
spring.liquibase.change-log=classpath:liquibase/dbchangelog.xml
spring.liquibase.contexts=definition,initialvalue,constraint
liquibase.change-log-path=src/main/resources/liquibase/dbchangelog.xml
liquibase.referenceUrl=hibernate:spring:net.robinfriedli.botify.entities?dialect=org.hibernate.dialect.PostgreSQL10Dialect
###############
# preferences #
###############
# replace this value with your YouTube API Quota: open the Google developer console and go to Library > YouTube Data API v3 > Manage > Quotas
botify.preferences.youtube_api_daily_quota=1000001
# partitioned = true means that data, such as playlists, presets and scripts will be separated between guilds
# if you host this bot privately and want to share data between few guilds you can set this property to 'false'
# however you should decide decide which mode to use before using the bot; if you have been using the bot with partitioned = true
# and several guilds have playlists or presets or scripts with the same name and you switch to partitioned = false then
# many queries will break because names are no longer unique
botify.preferences.mode_partitioned=true
botify.preferences.queue_size_max=10000
# maximum entity count per guild (if mode_partitioned = true, else entity count total)
botify.preferences.playlist_count_max=50
botify.preferences.playlist_size_max=5000
botify.preferences.preset_count_max=100
botify.preferences.script_count_max=100
botify.preferences.interceptor_count_max=10
# defines max heap size for the bootRun task
botify.preferences.maxHeapSize=2048m
# disable / enable commands in the scripting category and custom scripted command interceptors
botify.preferences.enableScripting=true
##############
# datasource #
##############
spring.datasource.url=jdbc:postgresql://localhost:5432/botify
spring.datasource.driverClassName=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQL10Dialect
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
```


### 6 Compile and run botify
Requires:
* java jdk 11 or above (preferably 11, as it is the version used in development and thus main supported version)
* rust and cargo-make with the `wasm32-unknown-unknown` target for the webapp

#### 6.1 Compile webapp
Install rust, preferably via rustup, then add the `wasm32-unknown-unknown` target by running `rustup target add wasm32-unknown-unknown` and install cargo-make with `cargo install --force cargo-make`. Finally, navigate to `src/main/webapp` and compile the webapp with `cargo make build`.

#### 6.2 Compile bot
Navigate to the project root directory and install botify by running `./gradlew build` (or if you have gradle installed you can just run `gradle build`).

#### 6.3 Run botify
Then you can launch botify using the jar file or the bootRun gradle task. You can either run the
jar file in `build/libs` by running `java -jar build/libs/botify-1.0-SNAPSHOT.jar` or run the bash script `bash/launch.sh`
or use the gradle bootRun task by running `./gradlew bootRun`. To keep the program running when closing the terminal window use a
terminal multiplexer tool like tmux (Unix-like operating systems (e.g. Linux or MacOS) only) to manage a terminal session.