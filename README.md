![# botify](https://raw.githubusercontent.com/robinfriedli/botify/master/resources-public/img/botify-logo-wide.png)
 Discord bot that plays Spotify tracks and YouTube videos or any URL including Soundcloud links.

* Play and search Spotify tracks and YouTube videos or playlists or any URL including Soundcloud links
* Create local playlists with tracks from any source
* Simple player commands
* Sign in to Spotify to play your own playlists or upload botify playlists
* Manage what roles can access which commands
* Give your bot a name

## Invite it to your guild

https://discordapp.com/oauth2/authorize?client_id=483377420494176258&permissions=3180544&scope=bot

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
#### 4.1 Navigate to your cloned project and go to ./resources and open the settings.properties file and fille in the blanks, it should look like this:
```properties
SERVER_PORT=8000
BASE_URI=http://localhost:8000
REDIRECT_URI=http://localhost:8000/login
DISCORD_TOKEN=#copy your discord token here
SPOTIFY_CLIENT_ID=#copy your spotify client id here
SPOTIFY_CLIENT_SECRET=#copy your spotify client secret here
YOUTUBE_CREDENTIALS=#copy your youtube credentials here
PLAYLISTS_PATH=./resources/playlists.xml
GUILD_PLAYLISTS_PATH=./resources/%splaylists.xml
GUILD_SPECIFICATION_PATH=./resources/guildSpecifications.xml
COMMANDS_PATH=./resources/commands.xml
COMMAND_INTERCEPTORS_PATH=./resources/commandInterceptors.xml
HTTP_HANDLERS_PATH=./resources/httpHandlers.xml
STARTUP_TASKS_PATH=./resources/startupTasks.xml
LOGIN_PAGE_PATH=./resources/login.html
LIST_PAGE_PATH=./resources/playlist_view.html
ERROR_PAGE_PATH=./resources/default_error_page.html
QUEUE_PAGE_PATH=./resources/queue_view.html
MODE_PARTITIONED=true
PLAYLIST_COUNT_MAX=50
PLAYLIST_SIZE_MAX=5000
```

### 5. Setup database
#### 5.1 Setup hibernate configuration
Navigate to ./resources/hibernate.cfg.xml and adjust the settings, if you use a local postgres server and name your
database botify_playlists you can leave it like this:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE hibernate-configuration PUBLIC
    "-//Hibernate/Hibernate Configuration DTD 3.0//EN"
    "http://www.hibernate.org/dtd/hibernate-configuration-3.0.dtd">

<hibernate-configuration>
  <session-factory>
    <property name="hibernate.connection.driver_class">org.postgresql.Driver</property>
    <property name="hibernate.connection.url">jdbc:postgresql://localhost:5432/botify_playlists</property>
    <property name="hibernate.connection.username">postgres</property>
    <property name="hibernate.connection.password">postgres</property>
    <property name="hibernate.dialect">org.hibernate.dialect.PostgreSQL94Dialect</property>
    <property name="show_sql">false</property>
    <property name="hibernate.hbm2ddl.auto">update</property>
  </session-factory>
</hibernate-configuration>
```
If you need help setting up your postgres server, please refer to their official documentation: http://www.postgresqltutorial.com/