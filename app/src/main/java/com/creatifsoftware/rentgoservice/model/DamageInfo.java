package com.creatifsoftware.rentgoservice.model;


import java.io.Serializable;

/**
 * Created by kerembalaban on 16.04.2019 at 16:32.
 */
public class DamageInfo implements Serializable {
    public long damageDate;
    public Branch damageBranch;
    public boolean isNewDamage = false;
}
