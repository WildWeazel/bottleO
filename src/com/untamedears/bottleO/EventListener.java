package com.untamedears.bottleO;

import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class EventListener implements Listener {

	// 1 stack of bottles for 832 xp, just enough for lvl 30
	protected static int XP_PER_BOTTLE = 13;
	// cool down time between bottling events, just to be safe
	protected static long WAIT_TIME_MILLIS = 5000;
	// if true, block all xp except from bottles
	protected static boolean DISABLE_EXPERIENCE=false;
	//cool-down timers
	protected static HashMap<String,Long> playerWaitHash = new HashMap<String,Long>(100);

	protected bottleO plugin;

	public EventListener(bottleO plugin) {
		this.plugin = plugin;
	}

	//change xp yield from bottle
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onExpBottleEvent(ExpBottleEvent e) {
		if(DISABLE_EXPERIENCE)
		{
			((Player) e.getEntity().getShooter()).giveExp(XP_PER_BOTTLE);
			e.setExperience(0);
		}
		else
		{
			e.setExperience(XP_PER_BOTTLE);
		}
	}
		
	@EventHandler
	public void onPlayerExpChangeEvent(PlayerExpChangeEvent j)
	{
		if(DISABLE_EXPERIENCE)
		{
			j.setAmount(0);			
		}		
	}
	
	//generate xp bottles
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerInteractEvent(PlayerInteractEvent e) {
		//check they have clicked on an enchanting table
		if ( e.getClickedBlock().getType() == Material.ENCHANTMENT_TABLE) {
			//if the player is holding glass bottles
			if (e.getMaterial() == Material.GLASS_BOTTLE) {
				Player p = e.getPlayer();
				//check player has waited for the required amount of time
				if (!playerWaitHash.containsKey(p.getName())) {
					//if there is no time recorded, add the current time
					bottleO.log.info("no record of "+p.getName()+" logging in!");
					playerWaitHash.put(p.getName(), System.currentTimeMillis());
				}

				long loginTime = playerWaitHash.get(p.getName());
				long timeDiff = System.currentTimeMillis() - (loginTime+WAIT_TIME_MILLIS);
				if (timeDiff < 0) {
					bottleO.log.info(p.getName()+" must wait "+(float)(-timeDiff)/1000+" seconds!");
					p.sendMessage(ChatColor.RED+"["+bottleO.pluginName+"]"+ChatColor.WHITE+" you must wait "+ChatColor.RED+(float)(-timeDiff)/1000+ChatColor.WHITE+" seconds to make more exp bottles.");
					return;
				}

				final int initialAmount = p.getItemInHand().getAmount();
				int amount = initialAmount;
				final int totalXP = p.getTotalExperience();

				if (totalXP < 0) {
					String infoMessage = "invalid xp values for "+p.getName()+", calculated xp:"+totalXP+", level:"+p.getLevel()+", progress:"+p.getExp()+", ";
					bottleO.log.info(infoMessage+"impossible xp value, stopping.");
					return;
				}

				int totalCost = initialAmount*XP_PER_BOTTLE;
				PlayerInventory inventory = p.getInventory();

				//sanity checking
				if (XP_PER_BOTTLE > 0 && initialAmount > 0 && initialAmount <= 64) {
					if (totalXP < totalCost) {
						if (totalXP >= XP_PER_BOTTLE) {
							totalCost = totalXP - (totalXP % XP_PER_BOTTLE);
							amount = totalCost / XP_PER_BOTTLE;
						} else {
							amount = 0;
						}
					}

					//if there is enough xp and bottles
					if (amount > 0) {
						//remove some glass bottles from hand
						ItemStack stack = p.getItemInHand();
						stack.setAmount(initialAmount-amount);
						p.setItemInHand(stack);

						//find the new xp value
						final int newTotalXP = totalXP - totalCost;
						int xpToGive = newTotalXP;


						// apparently setting the total XP does not update level calculation and vice versa. we'll do it live.
						int level = 0;
						p.setTotalExperience(0);
						p.setLevel(level);
						p.setExp(0);

						while(p.getExpToLevel() <= xpToGive) {
							xpToGive -= p.getExpToLevel();
							p.setLevel(++level);
						}
						float remainder = (float)xpToGive/p.getExpToLevel();
						p.setExp(remainder);
						p.setTotalExperience(newTotalXP);

						//try to put xp bottles in inventory
						HashMap<Integer, ItemStack> hash = inventory.addItem(new ItemStack(Material.EXP_BOTTLE, amount));
						//otherwise replace glass bottles in hand and drop glass bottles
						if (!hash.isEmpty()) {
							Iterator<Integer> it = hash.keySet().iterator();
							if (it.hasNext()) {
								ItemStack glassStack = p.getItemInHand().clone();
								p.setItemInHand(hash.get(it.next()));
								p.getWorld().dropItem(p.getLocation(), glassStack);
							}
						}

						//restart cool-down timer
						playerWaitHash.put(p.getName(), System.currentTimeMillis());
						//add potion effect because it looks cool
						p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 3));

						// log it all for sanity
						if(bottleO.log.isLoggable(Level.FINE)) {
							StringBuilder sb = new StringBuilder();
							sb.append("bottleO lite: ").append(p.getName())
								.append(", init: ").append(totalXP)
								.append(", final: ").append(p.getTotalExperience())
								.append(" (level ").append(p.getLevel() + p.getExp()).append(")")
								.append(", cost:").append(totalCost)
								.append(", bottles: ").append(amount);
							bottleO.log.fine(sb.toString());
						}						
					}
				}
			}
		}
	}

	//record login time
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onPlayerLoginEvent(PlayerLoginEvent e) {
		Player p = e.getPlayer();
		playerWaitHash.put(p.getName(), System.currentTimeMillis());
	}
}
