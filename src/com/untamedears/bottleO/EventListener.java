package com.untamedears.bottleO;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class EventListener implements Listener {

	protected static int XP_PER_BOTTLE = 25;
	protected static long WAIT_TIME_MILLIS = 5000;
	protected static int MAX_BOOKSHELVES = 30;
	protected static Random rand;
	//cool-down timers
	protected static HashMap<String,Long> playerWaitHash = new HashMap<String,Long>(100);

	protected bottleO plugin;

	public EventListener(bottleO plugin) {
		this.plugin = plugin;
	}

	//change xp yield from bottle
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onExpBottleEvent(ExpBottleEvent e) {
		e.setExperience(XP_PER_BOTTLE);
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
				int newTotalXP;

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
						//bottleO.log.info("Creating " + amount + "XP bottles");
						//remove some glass bottles from hand
						ItemStack stack = p.getItemInHand();
						stack.setAmount(initialAmount-amount);
						p.setItemInHand(stack);

						//set the new xp value
						newTotalXP = totalXP - totalCost;
						p.setTotalExperience(0);
						p.setTotalExperience(newTotalXP);
						//bottleO.log.info("Used " + totalCost + " of " + totalXP + " XP");

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
						//add slowness potion effect because it looks cool
						p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 3));
						bottleO.log.info("bottleO: "+p.getName()+", init:"+totalXP+", final:"+newTotalXP+", cost:"+totalCost+", bottles:"+(totalCost/XP_PER_BOTTLE));
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
