package cust.psdi.app.inventory;

import java.rmi.RemoteException;

import psdi.app.asset.AssetRemote;
import psdi.app.inventory.InvCost;
import psdi.app.inventory.Inventory;
import psdi.app.inventory.MatUseTrans;
import psdi.mbo.MboRemote;
import psdi.mbo.MboSet;
import psdi.mbo.MboSetRemote;
import psdi.mbo.SqlFormat;
import psdi.util.MXApplicationException;
import psdi.util.MXException;

public class MatUseTransCust extends MatUseTrans
{
	  public MatUseTransCust(MboSet ms) throws MXException, RemoteException
	  {
	    super(ms);
	  }
	  
	  public void save() throws MXException, RemoteException
	  {
		    super.save();

            final String[] params = { "Error: no se encontró el PPP." };

	        final Inventory invMbo = (Inventory)getSharedInventory();
	        if (invMbo == null) throw new MXApplicationException("messagebox", "CustomMessage", params);
	        	
	        InvCost invcost = (InvCost)getInvCostRecord(invMbo);
	        if (invcost == null) throw new MXApplicationException("messagebox", "CustomMessage", params);

            Double avgcost2 = Double.valueOf(invcost.getDouble("avgcost2"));
            setValue("linecost2", getDouble("quantity") * -1.0D * avgcost2.doubleValue(), 2L);
	  }
	  
	  ///AMB===v===
	  /// Reemplazada por la función de más abajo.
	  /// Error #5
	  /*
	  private MboRemote getInvCostRecord(MboRemote inventory) throws MXException, RemoteException
	  {
		    MboSetRemote invcostSet = inventory.getMboSet("INVCOST");
		    int i = 0;
		    MboRemote invCost = null;
		    while ((invCost = invcostSet.getMbo(i)) != null)
		    {
		      if (invCost.getString("itemnum").equals(getString("itemnum"))) {
		        if (invCost.getString("location").equals(getString("storeloc"))) {
		          if (invCost.getString("itemsetid").equals(getString("itemsetid"))) {
		            if ((invCost.getString("conditioncode").equals(getString("conditioncode"))) && 
		              (invCost.getString("siteid").equals(getString("siteid")))) {
		              return invCost;
		            }
		          }
		        }
		      }
		      i++;
		    }
		    return null;
	  }
	  */
	  ///AMB===^===
	  
	  private MboRemote getInvCostRecord(MboRemote inventory) throws MXException, RemoteException
	  {
          final String sql = "itemnum=:1 and location=:2 and itemsetid=:3 and conditioncode=:4 and siteid=:5";
          final SqlFormat sqf = new SqlFormat(this.getUserInfo(), sql);
          sqf.setObject(1, "INVCOST", "ITEMNUM", getString("itemnum"));
          sqf.setObject(2, "INVCOST", "LOCATION", getString("storeloc"));
          sqf.setObject(3, "INVCOST", "ITEMSETID", getString("itemsetid"));
          sqf.setObject(4, "INVCOST", "CONDITIONCODE", getString("conditioncode"));
          sqf.setObject(5, "INVCOST", "SITEID", getString("siteid"));

		  final MboSetRemote invCostSet = inventory.getMboSet("INVCOST$INVCOST", "INVCOST", sqf.format());
		  return invCostSet.getMbo(0);
	  }
}
