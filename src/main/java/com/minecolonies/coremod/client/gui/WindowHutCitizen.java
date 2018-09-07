package com.minecolonies.coremod.client.gui;

import com.minecolonies.api.util.LanguageHandler;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.blockout.Pane;
import com.minecolonies.blockout.controls.Button;
import com.minecolonies.blockout.controls.Label;
import com.minecolonies.blockout.controls.TextFieldVanilla;
import com.minecolonies.blockout.views.ScrollingList;
import com.minecolonies.coremod.MineColonies;
import com.minecolonies.coremod.colony.CitizenDataView;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingHome;
import com.minecolonies.coremod.network.messages.AssignUnassignMessage;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.util.constant.TranslationConstants.COM_MINECOLONIES_COREMOD_GUI_HOME_ASSIGN;
import static com.minecolonies.api.util.constant.TranslationConstants.COM_MINECOLONIES_COREMOD_GUI_WORKERHUTS_LEVEL_0;
import static com.minecolonies.api.util.constant.WindowConstants.LABEL_BUILDING_NAME;

/**
 * Window for the home building.
 */
public class WindowHutCitizen extends AbstractWindowBuilding<BuildingHome.View>
{
    /**
     * Id of the hire/fire button in the GUI.
     */
    private static final String BUTTON_ASSIGN = "assign";

    /**
     * Id of the hire/fire button in the GUI.
     */
    private static final String BUTTON_REMOVE = "remove";
    /**
     * The id of the button rename
     */
    private static final String BUTTON_RENAME = "rename";
    /**
     * The id of the field containing the new name
     */
    private static final String INPUT_NAME = "newName";
    /**
     * Id of the place where to name a citizen
     */
    private static final String ROW_NAME = "name";
    /**
     * Suffix describing the window xml.
     */
    private static final String HOME_BUILDING_RESOURCE_SUFFIX = ":gui/windowHutHome.xml";
    /**
     * Id to identify the list of the citizen in the view.
     */
    private static final String LIST_CITIZEN = "assignedCitizen";
    /**
     * The building the view is relates to.
     */
    private final BuildingHome.View home;
    /**
     * The list of citizen assigned to this hut.
     */
    private ScrollingList citizen;
    /**
     * The label where the name of the town will be displayed
     */
    private final Label title;

    /**
     * Creates the Window object.
     *
     * @param building View of the home building.
     */
    public WindowHutCitizen(final BuildingHome.View building)
    {
        super(building, Constants.MOD_ID + HOME_BUILDING_RESOURCE_SUFFIX);
        super.registerButton(BUTTON_RENAME, this::rename);
        super.registerButton(BUTTON_ASSIGN, this::assignClicked);
        super.registerButton(BUTTON_REMOVE, this::removeClicked);
        title = findPaneOfTypeByID(LABEL_BUILDING_NAME, Label.class);
        this.home = building;
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        final boolean isManualHousing = building.getColony().isManualHousing();
        title.setLabelText(building.getHouseName());
        citizen = findPaneOfTypeByID(LIST_CITIZEN, ScrollingList.class);
        citizen.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return home.getResidents().size();
            }

            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                final CitizenDataView citizenDataView = home.getColony().getCitizen((home.getResidents().get(index)));
                if (citizenDataView != null)
                {
                    rowPane.findPaneOfTypeByID(ROW_NAME, Label.class).setLabelText(citizenDataView.getName());
                    rowPane.findPaneOfTypeByID(BUTTON_REMOVE, Button.class).setEnabled(isManualHousing);
                }
            }
        });

        refreshView();
    }

    /**
     * Refresh the view.
     */
    private void refreshView()
    {
        final Button buttonAssign = findPaneOfTypeByID(BUTTON_ASSIGN, Button.class);

        final int sparePlaces = building.getBuildingLevel() - building.getResidents().size();
        buttonAssign.setLabel(LanguageHandler.format(COM_MINECOLONIES_COREMOD_GUI_HOME_ASSIGN, sparePlaces));
        buttonAssign.setEnabled(sparePlaces > 0 && building.getColony().isManualHousing());

        citizen.refreshElementPanes();
    }

    /**
     * Action when an assign button is clicked.
     */
    private void assignClicked()
    {
        if (building.getColony().isManualHousing())
        {
            if (building.getBuildingLevel() == 0)
            {
                LanguageHandler.sendPlayerMessage(Minecraft.getMinecraft().player, COM_MINECOLONIES_COREMOD_GUI_WORKERHUTS_LEVEL_0);
                return;
            }

            if (building.getResidents().size() < building.getBuildingLevel())
            {
                @NotNull final WindowAssignCitizen window = new WindowAssignCitizen(building.getColony(), building.getLocation());
                window.open();
            }
        }
    }

    /**
     * Action when the remove button is clicked.
     *
     * @param button the clicked button.
     */
    private void removeClicked(@NotNull final Button button)
    {
        if (building.getColony().isManualHousing())
        {
            final int row = citizen.getListElementIndexByPane(button);
            final int citizenid = home.getResidents().get(row);
            home.removeResident(row);
            MineColonies.getNetwork().sendToServer(new AssignUnassignMessage(building, false, citizenid));
            refreshView();
        }
    }

    /**
     * Returns the name of a building.
     *
     * @return Name of a building.
     */
    @NotNull
    @Override
    public String getBuildingName()
    {
        return "com.minecolonies.coremod.gui.workerHuts.homeHut";
    }

    /**
     * Function used to rename a player
     */
    private void rename()
    {
        TextFieldVanilla input = findPaneOfTypeByID(INPUT_NAME, TextFieldVanilla.class);
        building.setHouseName(input.getText());
        title.setLabelText(building.getHouseName());
    }
}
