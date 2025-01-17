////////////////////////////////////////////////////////////////////////////////////////////////////
// PlotSquared - A plot manager and world generator for the Bukkit API                             /
// Copyright (c) 2014 IntellectualSites/IntellectualCrafters                                       /
//                                                                                                 /
// This program is free software; you can redistribute it and/or modify                            /
// it under the terms of the GNU General Public License as published by                            /
// the Free Software Foundation; either version 3 of the License, or                               /
// (at your option) any later version.                                                             /
//                                                                                                 /
// This program is distributed in the hope that it will be useful,                                 /
// but WITHOUT ANY WARRANTY; without even the implied warranty of                                  /
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                                   /
// GNU General Public License for more details.                                                    /
//                                                                                                 /
// You should have received a copy of the GNU General Public License                               /
// along with this program; if not, write to the Free Software Foundation,                         /
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA                               /
//                                                                                                 /
// You can contact us via: support@intellectualsites.com                                           /
////////////////////////////////////////////////////////////////////////////////////////////////////
package com.intellectualcrafters.plot.commands;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotId;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.util.*;
import com.intellectualcrafters.plot.util.SchematicHandler.DataCollection;
import com.intellectualcrafters.plot.util.SchematicHandler.Dimension;
import com.intellectualcrafters.plot.util.SchematicHandler.Schematic;
import com.intellectualcrafters.plot.util.bukkit.BukkitUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class SchematicCmd extends SubCommand {
    private int counter = 0;
    private boolean running = false;
    private int task;

    public SchematicCmd() {
        super("schematic", "plots.schematic", "Schematic Command", "schematic {arg}", "sch", CommandCategory.ACTIONS, false);
        // TODO command to fetch schematic from worldedit directory
    }

    @Override
    public boolean execute(final PlotPlayer plr, final String... args) {
        if (args.length < 1) {
            sendMessage(plr, C.SCHEMATIC_MISSING_ARG);
            return true;
        }
        final String arg = args[0].toLowerCase();
        final String file;
        final Schematic schematic;
        switch (arg) {
            case "paste": {
                if (plr == null) {
                    PS.log(C.IS_CONSOLE.s());
                    return false;
                }
                if (!Permissions.hasPermission(plr, "plots.schematic.paste")) {
                    MainUtil.sendMessage(plr, C.NO_PERMISSION, "plots.schematic.paste");
                    return false;
                }
                if (args.length < 2) {
                    sendMessage(plr, C.SCHEMATIC_MISSING_ARG);
                    break;
                }
                final Location loc = plr.getLocation();
                final Plot plot = MainUtil.getPlot(loc);
                if (plot == null) {
                    sendMessage(plr, C.NOT_IN_PLOT);
                    return false;
                }
                if (this.running) {
                    MainUtil.sendMessage(plr, "&cTask is already running.");
                    return false;
                }
                final String file2 = args[1];
                this.running = true;
                this.counter = 0;
                TaskManager.runTaskAsync(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Schematic schematic = SchematicHandler.manager.getSchematic(file2);
                            if (schematic == null) {
                                sendMessage(plr, C.SCHEMATIC_INVALID, "non-existent or not in gzip format");
                                SchematicCmd.this.running = false;
                                return;
                            }
                            final int x;
                            final int z;
                            final Plot plot2 = MainUtil.getPlot(loc);
                            final Dimension dem = schematic.getSchematicDimension();
                            final Location bot = MainUtil.getPlotBottomLoc(loc.getWorld(), plot2.id).add(1, 0, 1);
                            final int length2 = MainUtil.getPlotWidth(loc.getWorld(), plot2.id);
                            if ((dem.getX() > length2) || (dem.getZ() > length2)) {
                                sendMessage(plr, C.SCHEMATIC_INVALID, String.format("Wrong size (x: %s, z: %d) vs %d ", dem.getX(), dem.getZ(), length2));
                                SchematicCmd.this.running = false;
                                return;
                            }
                            if ((dem.getX() != length2) || (dem.getZ() != length2)) {
                                final Location loc = plr.getLocation();
                                x = Math.min(length2 - dem.getX(), loc.getX() - bot.getX());
                                z = Math.min(length2 - dem.getZ(), loc.getZ() - bot.getZ());
                            } else {
                                x = 0;
                                z = 0;
                            }
                            final DataCollection[] b = schematic.getBlockCollection();
                            final int sy = BlockManager.manager.getHeighestBlock(bot);
                            final int WIDTH = schematic.getSchematicDimension().getX();
                            final int LENGTH = schematic.getSchematicDimension().getZ();
                            final Location l1;
                            if (!(schematic.getSchematicDimension().getY() == BukkitUtil.getMaxHeight(loc.getWorld()))) {
                                 l1 = bot.add(0, sy - 1, 0);
                            }
                             else {
                                 l1 = bot;
                             }
                            
                            final int blen = b.length - 1;
                            SchematicCmd.this.task = TaskManager.runTaskRepeat(new Runnable() {
                                @Override
                                public void run() {
                                    boolean result = false;
                                    while (!result) {
                                        final int start = SchematicCmd.this.counter * 5000;
                                        if (start > blen) {
                                            SchematicHandler.manager.pasteStates(schematic, plot, 0, 0);
                                            sendMessage(plr, C.SCHEMATIC_PASTE_SUCCESS);
                                            SchematicCmd.this.running = false;
                                            PS.get().TASK.cancelTask(SchematicCmd.this.task);
                                            return;
                                        }
                                        final int end = Math.min(start + 5000, blen);
                                        result = SchematicHandler.manager.pastePart(loc.getWorld(), b, l1, x, z, start, end, WIDTH, LENGTH);
                                        SchematicCmd.this.counter++;
                                    }
                                }
                            }, 1);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            SchematicCmd.this.running = false;
                            MainUtil.sendMessage(plr, "&cAn error occured, see console for more information");
                        }
                    }
                });
                break;
            }
            case "test": {
                if (plr == null) {
                    PS.log(C.IS_CONSOLE.s());
                    return false;
                }
                if (!Permissions.hasPermission(plr, "plots.schematic.test")) {
                    MainUtil.sendMessage(plr, C.NO_PERMISSION, "plots.schematic.test");
                    return false;
                }
                if (args.length < 2) {
                    sendMessage(plr, C.SCHEMATIC_MISSING_ARG);
                    return false;
                }
                final Location loc = plr.getLocation();
                final Plot plot = MainUtil.getPlot(loc);
                if (plot == null) {
                    sendMessage(plr, C.NOT_IN_PLOT);
                    return false;
                }
                file = args[1];
                schematic = SchematicHandler.manager.getSchematic(file);
                if (schematic == null) {
                    sendMessage(plr, C.SCHEMATIC_INVALID, "non-existent");
                    return false;
                }
                final int l1 = schematic.getSchematicDimension().getX();
                final int l2 = schematic.getSchematicDimension().getZ();
                final int length = MainUtil.getPlotWidth(loc.getWorld(), plot.id);
                if ((l1 < length) || (l2 < length)) {
                    sendMessage(plr, C.SCHEMATIC_INVALID, String.format("Wrong size (x: %s, z: %d) vs %d ", l1, l2, length));
                    break;
                }
                sendMessage(plr, C.SCHEMATIC_VALID);
                break;
            }
            case "saveall":
            case "exportall": {
                if (plr != null) {
                    MainUtil.sendMessage(plr, C.NOT_CONSOLE);
                    return false;
                }
                if (args.length != 2) {
                    MainUtil.sendMessage(null, "&cNeed world arg. Use &7/plots sch exportall <world>");
                    return false;
                }
                final HashMap<PlotId, Plot> plotmap = PS.get().getPlots(args[1]);
                if ((plotmap == null) || (plotmap.size() == 0)) {
                    MainUtil.sendMessage(plr, "&cInvalid world. Use &7/plots sch exportall <world>");
                    return false;
                }
                Collection<Plot> plots = plotmap.values();
                boolean result = SchematicHandler.manager.exportAll(plots, null, null, new Runnable() {
					@Override
					public void run() {
						MainUtil.sendMessage(plr, "&aFinished mass export");
					}
				});
                if (!result) {
                	MainUtil.sendMessage(plr, "&cTask is already running.");
                    return false;
                }
                else {
                	PS.log("&3PlotSquared&8->&3Schemaitc&8: &7Mass export has started. This may take a while.");
                    PS.log("&3PlotSquared&8->&3Schemaitc&8: &7Found &c" + plotmap.size() + "&7 plots...");
                }
                break;
            }
            case "export":
            case "save": {
                if (!Permissions.hasPermission(plr, "plots.schematic.save")) {
                    MainUtil.sendMessage(plr, C.NO_PERMISSION, "plots.schematic.save");
                    return false;
                }
                if (this.running) {
                    MainUtil.sendMessage(plr, "&cTask is already running.");
                    return false;
                }
                final String world;
                final Plot p2;
                if (plr != null) {
                    final Location loc = plr.getLocation();
                    final Plot plot = MainUtil.getPlot(loc);
                    if (plot == null) {
                        return !sendMessage(plr, C.NOT_IN_PLOT);
                    }
                    if (!plot.isAdded(plr.getUUID())) {
                        sendMessage(plr, C.NO_PLOT_PERMS);
                        return false;
                    }
                    p2 = plot;
                    world = loc.getWorld();
                } else {
                    if (args.length == 3) {
                        try {
                            world = args[1];
                            final String[] split = args[2].split(";");
                            final PlotId i = new PlotId(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                            if ((PS.get().getPlots(world) == null) || (PS.get().getPlots(world).get(i) == null)) {
                                MainUtil.sendMessage(null, "&cInvalid world or id. Use &7/plots sch save <world> <id>");
                                return false;
                            }
                            p2 = PS.get().getPlots(world).get(i);
                        } catch (final Exception e) {
                            MainUtil.sendMessage(null, "&cInvalid world or id. Use &7/plots sch save <world> <id>");
                            return false;
                        }
                    } else {
                        MainUtil.sendMessage(null, "&cInvalid world or id. Use &7/plots sch save <world> <id>");
                        return false;
                    }
                }
                
                Collection<Plot> plots = new ArrayList<Plot>();
                plots.add(p2);
                boolean result = SchematicHandler.manager.exportAll(plots, null, null, new Runnable() {
					@Override
					public void run() {
						MainUtil.sendMessage(plr, "&aFinished export");
						SchematicCmd.this.running = false;
					}
				});
                if (!result) {
                	MainUtil.sendMessage(plr, "&cTask is already running.");
                    return false;
                }
                else {
                	MainUtil.sendMessage(plr, "&7Starting export...");
                }
                break;
            }
            default: {
                sendMessage(plr, C.SCHEMATIC_MISSING_ARG);
                break;
            }
        }
        return true;
    }
}
