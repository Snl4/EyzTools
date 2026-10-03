package net.eyzencore.link.api.dto;

import java.util.List;

public final class StatsBatchRequest {

    public String server = "eyzencore";
    public String sentAt;
    public List<PlayerStatsDto> players;
}
