package com.minecolonies.coremod.commands.generalcommands;

import com.minecolonies.api.configuration.Configurations;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.coremod.colony.Colony;
import com.minecolonies.coremod.colony.ColonyManager;
import com.minecolonies.coremod.commands.AbstractSingleCommand;
import com.minecolonies.coremod.commands.ActionMenuState;
import com.minecolonies.coremod.commands.IActionCommand;
import com.minecolonies.coremod.commands.MinecoloniesCommand;
import com.minecolonies.coremod.util.ServerUtils;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.minecolonies.coremod.commands.AbstractSingleCommand.Commands.RTP;

/**
 * this command is made to TP a player to a safe random spot that is not to close to another colony.
 * Need to add a configs permissions check.
 * Need to allow OPs to send players ./mc ctp (Player) if player is not allowed.
 */
public class RandomTeleportCommand extends AbstractSingleCommand implements IActionCommand
{
    public static final  String DESC             = "rtp";
    private static final int    ATTEMPTS         = Configurations.gameplay.numberOfAttemptsForSafeTP;
    private static final int    UPPER_BOUNDS     = Configurations.gameplay.maxDistanceFromWorldSpawn * 2;
    private static final int    LOWER_BOUNDS     = Configurations.gameplay.maxDistanceFromWorldSpawn;
    private static final int    SPAWN_NO_TP      = Configurations.gameplay.minDistanceFromWorldSpawn;
    private static final int    STARTING_Y       = 250;
    private static final double SAFETY_DROP      = 6;
    private static final int    FALL_DISTANCE    = 5;
    private static final String CANT_FIND_PLAYER = "No player found for teleport, please define one.";

    /**
     * no-args constructor called by new CommandEntryPoint executer.
     */
    public RandomTeleportCommand()
    {
        super();
    }

    /**
     * Initialize this SubCommand with it's parents.
     *
     * @param parents an array of all the parents.
     */
    public RandomTeleportCommand(@NotNull final String... parents)
    {
        super(parents);
    }

    @NotNull
    @Override
    public String getCommandUsage(@NotNull final ICommandSender sender)
    {
        return super.getCommandUsage(sender) + "rtp" + "<playerName>";
    }

    @Override
    public void execute(@NotNull final MinecraftServer server, @NotNull final ICommandSender sender, @NotNull final ActionMenuState actionMenuState) throws CommandException
    {
        final EntityPlayer player = actionMenuState.getPlayerForArgument("player");
        executeShared(server, sender, ((null != player) ? player.getName() : null));
    }

    @Override
    public void execute(@NotNull final MinecraftServer server, @NotNull final ICommandSender sender, @NotNull final String... args) throws CommandException
    {
        String playerName = null;
        if (args.length != 0)
        {
            playerName = args[0];
        }
        executeShared(server, sender, playerName);
    }

    private void executeShared(@NotNull final MinecraftServer server, @NotNull final ICommandSender sender, final String playerName) throws CommandException
    {
        if (SPAWN_NO_TP >= LOWER_BOUNDS)
        {
            sender.sendMessage(new TextComponentString("Please have an admin raise the maxDistanceFromWorldSpawn number in config."));
            return;
        }

        if (!canCommandSenderUseCommand(RTP) || sender.getEntityWorld().provider.getDimension() != 0)
        {
            sender.sendMessage(new TextComponentString("Not happenin bro!!, ask an OP to TP you."));
            return;
        }

        EntityPlayer playerToTeleport = null;

        if (sender instanceof EntityPlayer)
        {
            playerToTeleport = (EntityPlayer) sender;
        }

        //If the arguments aren't empty, the sender probably wants to teleport another player.
        if ((null != playerName) && isPlayerOpped(sender))
        {
            final World world = FMLCommonHandler.instance().getMinecraftServerInstance().getEntityWorld();
            playerToTeleport =
              ServerUtils.getPlayerFromUUID(FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache()
                                              .getGameProfileForUsername(playerName).getId(), world);

            sender.sendMessage(new TextComponentString("TPing Player: " + playerToTeleport.getName()));
        }

        if (playerToTeleport == null)
        {
            sender.sendMessage(new TextComponentString(CANT_FIND_PLAYER));
            return;
        }
        teleportPlayer(sender, playerToTeleport);
    }

    /**
     * Method used to teleport the player.
     *
     * @param sender           the sender to have access to the world.
     * @param playerToTeleport the player which shall be teleported.
     */
    private static void teleportPlayer(final ICommandSender sender, final EntityPlayer playerToTeleport)
    {
        //Now the position will be calculated, we will try up to 4 times to find a save position.
        int attCounter = 0;
        while (attCounter <= ATTEMPTS)
        {
            attCounter++;
            /* this math is to get negative numbers */
            final int x = getRandCoordinate();
            final int z = getRandCoordinate();

            if (sender.getEntityWorld().getWorldBorder().getSize()
                    < (sender.getEntityWorld().getSpawnPoint().getDistance(x, sender.getEntityWorld().getSpawnPoint().getY(), z)))
            {
                continue;
            }

            final BlockPos tpPos = new BlockPos(x, STARTING_Y, z);

            final Colony colony = ColonyManager.getClosestColony(sender.getEntityWorld(), tpPos);
            /* Check for a close by colony*/
            if (colony != null && BlockPosUtil.getDistance2D(colony.getCenter(), tpPos) < Configurations.gameplay.workingRangeTownHall * 2 + Configurations.gameplay.townHallPadding)
            {
                continue;
            }

            /*Search for a ground position*/
            final BlockPos groundPosition = BlockPosUtil.findLand(tpPos, sender.getEntityWorld());

            /*If no position found*/
            if (groundPosition == null)
            {
                continue;
            }

            final boolean foundPosition = BlockPosUtil.isPositionSafe(sender.getEntityWorld(), groundPosition);

            if (foundPosition)
            {
                if (MinecoloniesCommand.canExecuteCommand((EntityPlayer) sender))
                {
                    playerToTeleport.sendMessage(new TextComponentString("Buckle up buttercup, this ain't no joy ride!!!"));
                    playerToTeleport.setHealth(playerToTeleport.getMaxHealth());
                    playerToTeleport.setPositionAndUpdate(groundPosition.getX(), groundPosition.getY() + SAFETY_DROP, groundPosition.getZ());
                    playerToTeleport.setHealth(playerToTeleport.getMaxHealth());

                    //.fallDistance is used to cancel out fall damage  basically if you have -5 it will reduce fall damage by 2.5 hearts
                    playerToTeleport.fallDistance = FALL_DISTANCE;
                }
                else
                {
                    sender.sendMessage(
                            new TextComponentString("Please wait at least " + Configurations.gameplay.teleportBuffer + " seconds to teleport again"));
                }
                return;
            }
        }
        sender.sendMessage(new TextComponentString("Couldn't find a safe spot.  Try again in a moment."));
    }

    /**
     * Get a random coordinate to teleport to.
     */
    private static int getRandCoordinate()
    {
        final Random rnd = new Random();

        int x = rnd.nextInt(UPPER_BOUNDS) - LOWER_BOUNDS;

        /* keeping X out of the spawn radius */
        while (x > -SPAWN_NO_TP && x < SPAWN_NO_TP)
        {
            x = rnd.nextInt(UPPER_BOUNDS) - LOWER_BOUNDS;
        }

        return x;
    }

    @NotNull
    @Override
    public List<String> getTabCompletionOptions(
                                                 @NotNull final MinecraftServer server,
                                                 @NotNull final ICommandSender sender,
                                                 @NotNull final String[] args,
                                                 final BlockPos pos)
    {
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(@NotNull final String[] args, final int index)
    {
        return index == 0;
    }
}



