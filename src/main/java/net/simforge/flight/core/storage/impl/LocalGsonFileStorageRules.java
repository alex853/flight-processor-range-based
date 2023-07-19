package net.simforge.flight.core.storage.impl;

public class LocalGsonFileStorageRules {
    public static String pilotFolderName(int pilotNumber) {
        String pilotNumberGroup10000 = (pilotNumber / 10000) + "xxxx";
        String pilotNumberGroup100 = (pilotNumber / 100) + "xx";
        return pilotNumberGroup10000 + "/" + pilotNumberGroup100 + "/" + pilotNumber;
    }
}
