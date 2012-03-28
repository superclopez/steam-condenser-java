/**
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the new BSD License.
 *
 * Copyright (c) 2008-2012, Sebastian Staudt
 */

package com.github.koraktor.steamcondenser.steam.community;

import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;
import com.github.koraktor.steamcondenser.steam.community.css.CSSStats;
import com.github.koraktor.steamcondenser.steam.community.defense_grid.DefenseGridStats;
import com.github.koraktor.steamcondenser.steam.community.dods.DoDSStats;
import com.github.koraktor.steamcondenser.steam.community.l4d.L4D2Stats;
import com.github.koraktor.steamcondenser.steam.community.l4d.L4DStats;
import com.github.koraktor.steamcondenser.steam.community.portal2.Portal2Stats;
import com.github.koraktor.steamcondenser.steam.community.tf2.TF2Stats;

/**
 * This class represents the game statistics for a single user and a specific
 * game
 * <p>
 * It is subclassed for individual games if the games provide special
 * statistics that are unique to this game.
 *
 * @author Sebastian Staudt
 */
public class GameStats {

    protected ArrayList<GameAchievement> achievements;

    protected int achievementsDone;

    protected String customUrl;

    protected SteamGame game;

    protected String hoursPlayed;

    protected String privacyState;

    protected Long steamId64;

    protected Element xmlData;

    /**
     * Creates a <code>GameStats</code> (or one of its subclasses) instance for
     * the given user and game
     *
     * @param steamId The custom URL or the 64bit Steam ID of the user
     * @param gameName The friendly name of the game
     * @return The game stats object for the given user and game
     * @throws SteamCondenserException if an error occurs while parsing the
     *         data
     */
    public static GameStats createGameStats(Object steamId, String gameName)
            throws SteamCondenserException {
        if(gameName.equals("cs:s")) {
            return new CSSStats(steamId);
        } else if(gameName.equals("defensegrid:awakening")) {
            return new DefenseGridStats(steamId);
        } else if(gameName.equals("dod:s")) {
            return new DoDSStats(steamId);
        } else if(gameName.equals("l4d")) {
            return new L4DStats(steamId);
        } else if(gameName.equals("l4d2")) {
            return new L4D2Stats(steamId);
        } else if(gameName.equals("portal2")) {
            return new Portal2Stats(steamId);
        } else if(gameName.equals("tf2")) {
            return new TF2Stats(steamId);
        } else {
            return new GameStats(steamId, gameName);
        }
    }

    /**
     * Returns the base Steam Communtiy URL for the given player and game IDs
     *
     * @param userId The 64bit SteamID or custom URL of the user
     * @param gameId The application ID or short name of the game
     * @return The base URL used for the given stats IDs
     */
    protected static String getBaseUrl(Object userId, Object gameId) {
        String gameUrl;
        if(gameId instanceof Integer) {
            gameUrl = "appid/" + gameId;
        } else {
            gameUrl = (String) gameId;
        }

        if(userId instanceof Integer) {
            return "http://steamcommunity.com/profiles/" + userId + "/stats/" + gameUrl;
        } else {
            return "http://steamcommunity.com/id/" + userId + "/stats/" + gameUrl;
        }
    }

    /**
     * Creates a <code>GameStats</code> object and fetches data from the Steam
     * Community for the given user and game
     *
     * @param steamId The custom URL or the 64bit Steam ID of the user
     * @param gameId The app ID or friendly name of the game
     * @throws SteamCondenserException if the stats cannot be fetched
     */
    protected GameStats(Object steamId, Object gameId)
            throws SteamCondenserException {
        if(steamId instanceof String) {
            this.customUrl = (String) steamId;
        } else if(steamId instanceof Long) {
            this.steamId64 = (Long) steamId;
        }

        try {
            String url = getBaseUrl(steamId, gameId) + "?xml=all";

            DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            this.xmlData = parser.parse(url).getDocumentElement();

            NodeList errorNode = this.xmlData.getElementsByTagName("error");
            if(errorNode.getLength() > 0) {
                throw new SteamCondenserException(errorNode.item(0).getTextContent());
            }

            this.privacyState = this.xmlData.getElementsByTagName("privacyState").item(0).getTextContent();
            if(this.isPublic()) {
                int appId = Integer.parseInt(((Element) this.xmlData.getElementsByTagName("game").item(0)).getElementsByTagName("gameLink").item(0).getTextContent().replace("http://store.steampowered.com/app/", ""));
                this.game = SteamGame.create(appId, (Element) this.xmlData.getElementsByTagName("game").item(0));

                Node hoursPlayedNode = ((Element) this.xmlData.getElementsByTagName("stats").item(0)).getElementsByTagName("hoursPlayed").item(0);
                if(hoursPlayedNode != null) {
                    this.hoursPlayed = hoursPlayedNode.getTextContent();
                }

                if(this.customUrl == null) {
                    this.customUrl = ((Element) this.xmlData.getElementsByTagName("player").item(0)).getElementsByTagName("customURL").item(0).getTextContent();
                }
                if(this.steamId64 == null) {
                    this.steamId64 = Long.parseLong(((Element) this.xmlData.getElementsByTagName("player").item(0)).getElementsByTagName("steamID64").item(0).getTextContent().trim());
                }
            }
        } catch(Exception e) {
            throw new SteamCondenserException("XML data could not be parsed.", e);
        }
    }

    /**
     * Returns the achievements for this stats' user and game
     * <p>
     * If the achievements' data hasn't been parsed yet, parsing is done now.
     *
     * @return All achievements belonging to this game
     */
    public ArrayList<GameAchievement> getAchievements() {
        if(this.achievements == null) {
            this.achievements = new ArrayList<GameAchievement>();
            this.achievementsDone = 0;

            NodeList achievementsList = ((Element) this.xmlData.getElementsByTagName("achievements").item(0)).getElementsByTagName("achievement");
            for(int i = 0; i < achievementsList.getLength(); i++) {
                Element achievementData = (Element) achievementsList.item(i);
                GameAchievement achievement = new GameAchievement(this.steamId64, this.game.getAppId(), achievementData);
                if(achievement.isUnlocked()) {
                    this.achievementsDone += 1;
                }
                this.achievements.add(achievement);
            }
        }

        return achievements;
    }

    /**
     * Returns the number of achievements done by this player
     * <p>
     * If achievements haven't been parsed yet for this player and this game,
     * parsing is done now.
     *
     * @return The number of achievements completed
     * @see #getAchievements
     */
    public int getAchievementsDone() {
        if(this.achievements == null) {
            this.getAchievements();
        }

        return this.achievementsDone;
    }

    /**
     * Returns the percentage of achievements done by this player
     * <p>
     * If achievements haven't been parsed yet for this player and this game,
     * parsing is done now.
     *
     * @return The percentage of achievements completed
     * @see #getAchievementsDone
     */
    public float getAchievementsPercentage() {
        return (float) this.getAchievementsDone() / this.achievements.size();
    }

    /**
     * Returns the base Steam Communtiy URL for the stats contained in this
     * object
     *
     * @return The base URL used for queries on these stats
     */
    public String getBaseUrl() {
        if(this.customUrl == null) {
            return getBaseUrl(this.steamId64, this.game.getId());
        } else {
            return getBaseUrl(this.customUrl, this.game.getId());
        }
    }


    /**
     * Returns the custom URL of the player these stats belong to
     *
     * @return The custom URL of the player
     */
    public String getCustomUrl() {
        return this.customUrl;
    }

    /**
     * Returns the game these stats belong to
     *
     * @return The game object
     */
    public SteamGame getGame() {
        return this.game;
    }

    /**
     * Returns the privacy setting of the Steam ID profile
     *
     * @return The privacy setting of the Steam ID
     */
    public String getPrivacyState() {
        return this.privacyState;
    }

    /**
     * Returns the number of hours this game has been played by the player
     *
     * @return The number of hours this game has been played
     */
    public String getHoursPlayed() {
        return this.hoursPlayed;
    }

    /**
     * Returns the 64bit numeric SteamID of the player these stats belong to
     *
     * @return The 64bit numeric SteamID of the player
     */
    public long getSteamId64() {
        return this.steamId64;
    }

    /**
     * Returns whether this Steam ID is publicly accessible
     *
     * @return <code>true</code> if this Steam ID is publicly accessible
     */
    protected boolean isPublic() {
        return this.privacyState.equals("public");
    }
}
