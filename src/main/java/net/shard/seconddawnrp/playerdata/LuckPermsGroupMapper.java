package net.shard.seconddawnrp.playerdata;

import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;

public class LuckPermsGroupMapper {

    public String getDivisionGroup(Division division) {
        return "st.division." + division.getId();
    }

    public String getRankGroup(Rank rank) {
        return "st.rank." + rank.getId();
    }

    public String getBilletGroup(Billet billet) {
        return "st.billet." + billet.getId();
    }

    public String getCertificationNode(Certification certification) {
        return "st.cert." + certification.getId();
    }
}
