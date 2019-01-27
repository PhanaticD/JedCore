package com.jedk1.jedcore.ability.waterbending;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.command.Commands;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.jedk1.jedcore.JedCore;
import com.jedk1.jedcore.util.RegenTempBlock;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.waterbending.Torrent;

public class FrostBreath extends IceAbility implements AddonAbility {
	private long time;
	private Material[] invalidBlocks = {
			Material.ICE,
			Material.LAVA,
			Material.AIR,
			Material.VOID_AIR,
			Material.CAVE_AIR
	};
	private Biome[] invalidBiomes = {
			Biome.DESERT,
			Biome.DESERT_HILLS,
			Biome.NETHER,
			Biome.BADLANDS,
			Biome.BADLANDS_PLATEAU,
			Biome.ERODED_BADLANDS,
			Biome.SAVANNA,
			Biome.SAVANNA_PLATEAU
	};

	private long cooldown;
	private long duration;
	private int particles;
	private int freezeDuration;
	private int snowDuration;
	private int range;
	private boolean snowEnabled;
	private boolean bendSnow;
	private boolean damageEnabled;
	private double playerDamage;
	private double mobDamage;
	private boolean slowEnabled;
	private long slowDuration;
	private boolean restrictBiomes;

	public FrostBreath(Player player) {
		super(player);
		if (!bPlayer.canBend(this) || !bPlayer.canIcebend()) {
			return;
		}
		setFields();
		Location temp = player.getLocation();
		Biome biome = temp.getWorld().getBiome(temp.getBlockX(), temp.getBlockZ());
		if (restrictBiomes && !isValidBiome(biome)) {
			return;
		}
		time = System.currentTimeMillis();
		start();
	}

	public void setFields() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);

		cooldown = config.getLong("Abilities.Water.FrostBreath.Cooldown");
		duration = config.getLong("Abilities.Water.FrostBreath.Duration");
		particles = config.getInt("Abilities.Water.FrostBreath.Particles");
		freezeDuration = config.getInt("Abilities.Water.FrostBreath.FrostDuration");
		snowDuration = config.getInt("Abilities.Water.FrostBreath.SnowDuration");
		range = config.getInt("Abilities.Water.FrostBreath.Range");
		snowEnabled = config.getBoolean("Abilities.Water.FrostBreath.Snow");
		bendSnow = config.getBoolean("Abilities.Water.FrostBreath.BendableSnow");
		damageEnabled = config.getBoolean("Abilities.Water.FrostBreath.Damage.Enabled");
		playerDamage = config.getDouble("Abilities.Water.FrostBreath.Damage.Player");
		mobDamage = config.getDouble("Abilities.Water.FrostBreath.Damage.Mob");
		slowEnabled = config.getBoolean("Abilities.Water.FrostBreath.Slow.Enabled");
		slowDuration = config.getLong("Abilities.Water.FrostBreath.Slow.Duration");
		restrictBiomes = config.getBoolean("Abilities.Water.FrostBreath.RestrictBiomes");
	}

	@Override
	public void progress() {
		if (player == null || !player.isOnline()) {
			remove();
			return;
		}
		if (!bPlayer.canBendIgnoreCooldowns(this)) {
			bPlayer.addCooldown(this);
			remove();
			return;
		}
		if (!player.isSneaking() || player.isDead()) {
			bPlayer.addCooldown(this);
			remove();
			return;
		}
		if (System.currentTimeMillis() < time + duration) {
			createBeam();
		} else {
			bPlayer.addCooldown(this);
			remove();
			return;
		}
	}

	private boolean isLocationSafe(Location loc) {
		Block block = loc.getBlock();
		if (GeneralMethods.isRegionProtectedFromBuild(player, "FrostBreath", loc)) {
			return false;
		}
		if (!isTransparent(block)) {
			return false;
		}
		return true;
	}
	
	public boolean isValidBiome(Biome biome) {
		return !Arrays.asList(invalidBiomes).contains(biome);
	}

	private void createBeam() {
		Location loc = player.getEyeLocation();
		Vector dir = player.getLocation().getDirection();
		double step = 1;
		double size = 0;
		double offset = 0;
		double damageregion = 1.5;

		for (double i = 0; i < range; i += step) {
			loc = loc.add(dir.clone().multiply(step));
			size += 0.005;
			offset += 0.3;
			damageregion += 0.01;

			if (!isLocationSafe(loc))
				return;

			for (Entity entity : GeneralMethods.getEntitiesAroundPoint(loc, damageregion)) {
				if (entity instanceof LivingEntity && entity.getEntityId() != player.getEntityId()) {

					for (Location l2 : createCage(entity.getLocation())) {
						if (!GeneralMethods.isRegionProtectedFromBuild(this, l2) && (!l2.getBlock().getType().isSolid() || ElementalAbility.isAir(l2.getBlock().getType())) && !((entity instanceof Player) && Commands.invincible.contains(((Player) entity).getName()))) {
							Block block = l2.getBlock();

							TempBlock ice = new TempBlock(block, Material.ICE);
							ice.setRevertTime(freezeDuration);
						}
					}

					if (slowEnabled) {
						((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, (int) slowDuration / 50, 5));
					}
					if (damageEnabled) {
						if (entity instanceof Player) {
							DamageHandler.damageEntity(entity, playerDamage, this);
						} else {
							DamageHandler.damageEntity(entity, mobDamage, this);
						}
					}
				}
			}

			if (snowEnabled) {
				freezeGround(loc);
			}

			ParticleEffect.SNOW_SHOVEL.display(loc, particles, Math.random(), Math.random(), Math.random(), size);
			ParticleEffect.SPELL_MOB.display(getOffsetLocation(loc, offset), 0, 220, 220, 220, 0.003, new Particle.DustOptions(Color.fromRGB(220, 220, 220), 1));
			ParticleEffect.SPELL_MOB.display(getOffsetLocation(loc, offset), 0, 150, 150, 255, 0.0035, new Particle.DustOptions(Color.fromRGB(150, 150, 255), 1));
		}
	}

	private Location getOffsetLocation(Location loc, double offset) {
		return loc.clone().add((float) ((Math.random() - 0.5) * offset), (float) ((Math.random() - 0.5) * offset), (float) ((Math.random() - 0.5) * offset));
	}

	private void freezeGround(Location loc) {
		for (Location l : GeneralMethods.getCircle(loc, 2, 2, false, true, 0)) {
			if (!GeneralMethods.isRegionProtectedFromBuild(player, "FrostBreath", l)) {
				Block block = l.getBlock();
				if (isWater(l.getBlock())) {
					TempBlock temp = new TempBlock(block, Material.ICE, Material.ICE.createBlockData());
				} else if (isTransparent(l.getBlock()) && l.clone().add(0, -1, 0).getBlock().getType().isSolid() && !Arrays.asList(invalidBlocks).contains(l.clone().add(0, -1, 0).getBlock().getType())) {
					boolean createTemp = !bendSnow;

					// Stop this from overwriting LavaFlow TempBlocks.
					// This fixes a bug where using FrostBreath against LavaFlow creates a permanent hole in the ground.
					if (isLava(block) && TempBlock.isTempBlock(block)) {
						createTemp = true;
					}

					new RegenTempBlock(block, Material.SNOW, Material.SNOW.createBlockData(), snowDuration, createTemp);
				}
			}
		}
	}

	private List<Location> createCage(Location centerBlock) {
		List<Location> selectedBlocks = new ArrayList<Location>();

		int bX = centerBlock.getBlockX();
		int bY = centerBlock.getBlockY();
		int bZ = centerBlock.getBlockZ();

		for (int x = bX - 1; x <= bX + 1; x++) {
			for (int y = bY - 1; y <= bY + 1; y++) {
				Location l = new Location(centerBlock.getWorld(), x, y, bZ);
				selectedBlocks.add(l);
			}
		}

		for (int y = bY - 1; y <= bY + 2; y++) {
			Location l = new Location(centerBlock.getWorld(), bX, y, bZ);
			selectedBlocks.add(l);
		}

		for (int z = bZ - 1; z <= bZ + 1; z++) {
			for (int y = bY - 1; y <= bY + 1; y++) {
				Location l = new Location(centerBlock.getWorld(), bX, y, z);
				selectedBlocks.add(l);
			}
		}

		for (int x = bX - 1; x <= bX + 1; x++) {
			for (int z = bZ - 1; z <= bZ + 1; z++) {
				Location l = new Location(centerBlock.getWorld(), x, bY, z);
				selectedBlocks.add(l);
			}
		}

		return selectedBlocks;
	}
	
	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public Location getLocation() {
		return null;
	}

	@Override
	public String getName() {
		return "FrostBreath";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public String getAuthor() {
		return JedCore.dev;
	}

	@Override
	public String getVersion() {
		return JedCore.version;
	}

	@Override
	public String getDescription() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);
		return "* JedCore Addon *\n" + config.getString("Abilities.Water.FrostBreath.Description");
	}

	@Override
	public void load() {
		return;
	}

	@Override
	public void stop() {
		return;
	}
	
	@Override
	public boolean isEnabled() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);
		return config.getBoolean("Abilities.Water.FrostBreath.Enabled");
	}
}
