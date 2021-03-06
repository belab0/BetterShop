package com.nhksos.jjfs85.BetterShop;

import com.jascotty2.io.FileIO;
import com.jascotty2.io.CheckInput;
import com.jascotty2.Item.JItem;
import com.jascotty2.Item.JItemDB;
import com.jascotty2.Shop.PriceList;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import java.util.LinkedList;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;

public class BSPriceList extends PriceList {

    public BSPriceList() {
        // load the pricelist.
        // load();
    }

    public final boolean load() {
        sortOrder = BetterShop.config.sortOrder;
        useCache = BetterShop.config.useDBCache;
        dbCacheTTL = BetterShop.config.priceListLifespan;//.intValue();
        if (BetterShop.config.useMySQL()) {
            try {
                //System.out.println("attempting MySQL");
                if (loadMySQL(BetterShop.config.sql_database,
                        BetterShop.config.tableName,
                        BetterShop.config.sql_username,
                        BetterShop.config.sql_password,
                        BetterShop.config.sql_hostName,
                        BetterShop.config.sql_portNum)) {
                    BetterShop.Log(Level.INFO, "MySQL database " + pricelistName() + " loaded.");
                    loadOldPricelist();
                    return true;
                }
            } catch (Exception ex) {
                BetterShop.Log(Level.SEVERE, ex, false);
            }
            BetterShop.Log(Level.SEVERE, "Failed to connect to MySQL database "
                    + BetterShop.config.sql_database, false);

        } else {
            try {
                if (loadFile(new File(BSConfig.pluginFolder.getPath() + File.separatorChar
                        + BetterShop.config.tableName + ".csv"))) {
                    BetterShop.Log(Level.INFO, BetterShop.config.tableName + ".csv loaded.");
                    loadOldPricelist();
                    return true;
                }
            } catch (IOException ex) {
                BetterShop.Log(Level.SEVERE, ex, false);
            } catch (Exception ex) {
                BetterShop.Log(Level.SEVERE, ex);
            }
            BetterShop.Log(Level.SEVERE, "Failed to load pricelist database " + BetterShop.config.tableName + ".csv "
                    + BetterShop.config.sql_database, false);
        }
        return false;
    }

    public void loadOldPricelist() {
        File oldFile = new File(BSConfig.pluginFolder.getPath() + File.separatorChar + "PriceList.yml");
        if (loadOldPricelist(oldFile)) {
            oldFile.renameTo(new File(BSConfig.pluginFolder.getPath() + File.separatorChar + "OLD_PriceList.yml"));
            BetterShop.Log("Old Pricelist Imported Successfully ");
        }
    }

    public boolean loadOldPricelist(File oldFile) {
        if (oldFile.exists()) {
            try {
                BetterShop.Log("Found old PriceList.yml file");
                Configuration PriceList = new Configuration(oldFile);
                PriceList.load();
                if (PriceList.getNode("prices") != null) {
                    for (String itn : PriceList.getKeys("prices")) {
                        if (itn.startsWith("item")) {
                            int id = 0, sub = 0;
                            if (itn.contains("sub")) {
                                id = CheckInput.GetInt(itn.substring(4, itn.indexOf("sub")), 0);
                                sub = CheckInput.GetInt(itn.substring(itn.indexOf("sub") + 3), 0);
                            } else {
                                id = CheckInput.GetInt(itn.substring(4), 0);
                            }
                            JItem toAdd = JItemDB.GetItem(id, (byte) sub);
                            if (toAdd != null) {
                                //if(setPrice(toAdd, PriceList.getDouble("prices." + itn + ".buy", -1), PriceList.getDouble("prices." + itn + ".sell", -1)))
                                //System.out.println("Added " + toAdd);
                                if (BetterShop.config.useItemStock && BetterShop.stock != null && !ItemExists(toAdd)) {
                                    BetterShop.stock.setItemAmount(toAdd, BetterShop.config.startStock);
                                }
                                setPrice(toAdd, PriceList.getDouble("prices." + itn + ".buy", -1), PriceList.getDouble("prices." + itn + ".sell", -1));
                            } else {
                                BetterShop.Log("Invalid Item: " + itn);
                            }
                        } else {
                            BetterShop.Log("Invalid Item: " + itn);
                        }
                    }
                }
                save();
                return true;
            } catch (IOException ex) {
                BetterShop.Log(Level.SEVERE, ex, false);
            } catch (Exception ex) {
                BetterShop.Log(Level.SEVERE, ex);
            }
        }
        return false;
    }

    public boolean setPrice(String item, String b, String s) {
        return setPrice(JItemDB.findItem(item),
                CheckInput.GetDouble(b, -1),
                CheckInput.GetDouble(s, -1));
    }
    
    public boolean setPrice(JItem item, String b, String s) {
        return setPrice(item,
                CheckInput.GetDouble(b, -1),
                CheckInput.GetDouble(s, -1));
    }

    @Override
    public boolean setPrice(JItem item, double b, double s) {
        try {
            if (!BSutils.decimalSupported()) {
                // decimals not supported: round
                b = Math.round(b);
                s = Math.round(s);
            }
            
            return super.setPrice(item, b, s);
        } catch (SQLException ex) {
            BetterShop.Log(Level.SEVERE, ex);
        } catch (IOException ex) {
            BetterShop.Log(Level.SEVERE, ex, false);
        } catch (Exception ex) {
            BetterShop.Log(Level.SEVERE, ex);
        }
        return false;
    }

    public double itemBuyPrice(Player player, JItem toBuy) {
        try {
            if (!BSutils.decimalSupported()) {
                return Math.round(BetterShop.pricelist.getBuyPrice(toBuy));
            } else {
                return BetterShop.pricelist.getBuyPrice(toBuy);
            }
        } catch (Exception ex) {
            BetterShop.Log(Level.SEVERE, ex);
            /*if (load(null)) {
            // ask to try command again.. don't want accidental infinite recursion & don't want to plan for recursion right now
            BSutils.sendMessage(player, "Success! Please try again.. ");
            } else {
            BSutils.sendMessage(player, ChatColor.RED + "Failed! Please let an OP know of this error");
            }*/
            BSutils.sendMessage(player, "Error looking up price"); // .. Attempting DB reload..
            return Double.NEGATIVE_INFINITY;
        }
    }

    public LinkedList<String> GetShopListPage(int pageNum, boolean isPlayer, boolean showIllegal) {
        try {
            return GetShopListPage(pageNum, isPlayer,
                    BetterShop.config.pagesize,
                    BetterShop.config.getString("listing"),
                    BetterShop.config.getString("listhead"),
                    BetterShop.config.getString("listtail"), showIllegal,
                    BSutils.decimalSupported(), BetterShop.stock);
        } catch (Exception ex) {
            BetterShop.Log(Level.SEVERE, ex);
        }
        // an error occured: let player know
        LinkedList<String> ret = new LinkedList<String>();
        ret.add("\u00A74An Error Occurred while retrieving pricelist.. ");
        ret.add("\u00A74 see the server log or let an OP know of this error");
        return ret;
    }

    public LinkedList<String> GetShopListPage(int pageNum, boolean isPlayer) {
        return GetShopListPage(pageNum, isPlayer, true);
    }

    public boolean restoreDB(File toImport) {
        try {
            removeAll();
            return importDB(toImport);
        } catch (IOException ex) {
            BetterShop.Log(Level.SEVERE, ex, false);
        } catch (Exception ex) {
            BetterShop.Log(Level.SEVERE, ex);
        }
        return false;
    }

    public boolean importDB(File toImport) {
        if (toImport.getName().toLowerCase().endsWith(".yml")) {
            if (BetterShop.pricelist.loadOldPricelist(toImport)) {
                return true;
            }
        } else {
            try {
                ArrayList<String[]> db = FileIO.loadCSVFile(toImport);
                int n = 0;//num = 0, 
                for (String fields[] : db) {
                    if (fields.length > 3) {
                        if (fields.length > 3) {
                            //JItem plItem = JItem.findItem(fields[0] + ":" + (fields[1].length() == 0 ? "0" : fields[1]));
                            JItem plItem = JItemDB.findItem(fields[0] + ":" + (fields[1].equals(" ") ? "0" : fields[1]));
                            if (plItem != null) {
                                BetterShop.pricelist.setPrice(plItem,
                                        fields[2].equals(" ") ? -1 : CheckInput.GetDouble(fields[2], -1),
                                        fields[3].equals(" ") ? -1 : CheckInput.GetDouble(fields[3], -1));
                                //++num;
                            } else if (n > 0) { // first line is expected invalid: is title
                                BetterShop.Log(Level.WARNING, String.format("Invalid item on line %d in %s", (n + 1), toImport.getName()));
                            }
                        } else {
                            BetterShop.Log(Level.WARNING, String.format("unexpected pricelist line at %d in %s", (n + 1), toImport.getName()));
                        }
                    }
                }
                return true;
            } catch (IOException ex) {
                BetterShop.Log(Level.SEVERE, ex, false);
            } catch (Exception ex) {
                BetterShop.Log(Level.SEVERE, ex);
            }
        }
        return false;
    }
}
