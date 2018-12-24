# botify
Discord bot that plays Spotify tracks and YouTube videos.

* Play Spotify tracks and YouTube videos or playlists
* Create local playlists with both YouTube videos and Spotify songs
* Simple player commands
* Sign in to Spotify to play your own playlists

## Invite it to your guild

https://discordapp.com/api/oauth2/authorize?client_id=483377420494176258&permissions=3147776&scope=bot

## Host it yourself

### 1. Create a discord app

#### 1.1 Go to https://discordapp.com/developers/applications and create an application
#### 1.2 Click "Bot" on the side menu to create a bot and copy the token for later
#### 1.3 Go to https://developer.spotify.com/dashboard/applications to create a Spotify application and copy the client id
#### 1.4 Click on "Edit Settings" and whitelist your Redirect URI for the Spotify login
Don't have a domain? You could either go without logins all together and still use most of botify's features or use your
router's public ip and setup port forwarding for your router.
#### 1.5 Go to https://console.developers.google.com/ and create a project for the YouTube Data API and create and copy the credentials
#### 1.6 Navigate to your cloned project and go to ./resources and create the settings.properties file, it should look like this:
```
SERVER_PORT=8000
LOGIN_CONTEXT_PATH=/login
REDIRECT_URI=http://localhost:8000/login
DISCORD_TOKEN=#copy your discord token here
SPOTIFY_CLIENT_ID=#copy your spotify client id here
SPOTIFY_CLIENT_SECRET=#copy your spotify client secret here
YOUTUBE_CREDENTIALS=#copy your youtube credentials here
PLAYLISTS_PATH=./resources/playlists.xml
GUILD_PLAYLISTS_PATH=./resources/%splaylists.xml
GUILD_SPECIFICATION_PATH=./resources/guildSpecifications.xml
MODE_PARTITIONED=true
```