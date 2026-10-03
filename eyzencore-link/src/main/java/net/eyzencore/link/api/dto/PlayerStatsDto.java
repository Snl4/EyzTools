package net.eyzencore.link.api.dto;

import java.util.Map;

public final class PlayerStatsDto {

    public String uuid;
    public String nickname;
    public boolean online;
    public String firstJoin;
    public String lastSeen;
    public long playtime;
    public int deaths;
    public int mobKills;
    public int playerKills;
    public long blocksMined;
    public long blocksPlaced;
    public long distance;
    public int advancements;
    public int advancementsTotal;
    public Map<String, Integer> customMobKills;
}
