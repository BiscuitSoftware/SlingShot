package xyz.olivermartin.slingshot;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.plugin.Command;

public class SlingShotCommand extends Command {

	public SlingShotCommand() {
		super("slingshot", "slingshot.admin", new String[0]);
	}

	@Override
	public void execute(CommandSender sender, String[] args) {

		if (args.length != 1) return;

		if (args[0].toLowerCase().equals("reload")) {
			sender.sendMessage(new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', "&aPreparing to reload config!")).create());
			SlingShot.configman.startupConfig();
			sender.sendMessage(new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', "&6Reload completed!")).create());
			sender.sendMessage(new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', "&aIf any errors occured they can be viewed in the console log!")).create());
		}

		if (args[0].toLowerCase().equals("debug")) {
			SlingShot.debug = !SlingShot.debug;
		}

	}

}
