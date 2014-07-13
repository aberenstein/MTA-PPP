package psdi.app.inventory;

import psdi.app.asset.AssetSetRemote;
import psdi.app.item.ItemCondition;
import psdi.app.item.ItemConditionSet;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.List;
import java.util.Collections;
import psdi.app.contract.ContractRemote;
import psdi.app.contract.ContractSetRemote;
import psdi.app.contract.ContractLineRemote;
import psdi.app.po.PORemote;
import psdi.app.po.POSetRemote;
import psdi.app.po.POLineRemote;
import psdi.app.pr.PRRemote;
import psdi.app.pr.PRSetRemote;
import psdi.app.pr.PRLineRemote;
import psdi.app.mr.MRRemote;
import psdi.app.mr.MRSetRemote;
import psdi.app.mr.MRLineRemote;
import psdi.app.jobplan.JobPlanRemote;
import psdi.app.jobplan.JobPlanSetRemote;
import psdi.app.jobplan.JobItemRemote;
import psdi.app.workorder.WORemote;
import psdi.app.workorder.WOSetRemote;
import psdi.app.workorder.WPItemRemote;
import psdi.app.asset.SparePartSetRemote;
import psdi.app.inventory.virtual.IssueItemToAssetRemote;
import psdi.util.BidiUtils;
import psdi.app.financial.FinancialServiceRemote;
import psdi.app.item.ItemStructRemote;
import psdi.app.item.ItemStructSetRemote;
import psdi.security.ProfileRemote;
import psdi.util.MXFormat;
import psdi.mbo.Translate;
import psdi.server.AppServiceRemote;
import psdi.mbo.SqlFormat;
import java.util.ArrayList;
import psdi.app.asset.AssetRemote;
import psdi.server.AppService;
import psdi.app.integration.IntegrationServiceRemote;
import java.util.Date;
import psdi.mbo.NonPersistentMboRemote;
import psdi.util.MXMath;
import psdi.server.MXServer;
import psdi.app.item.ItemOrgInfoSetRemote;
import psdi.mbo.MboRemote;
import psdi.app.item.ItemRemote;
import psdi.mbo.StatusHandler;
import psdi.mbo.MboSetRemote;
import java.rmi.RemoteException;
import psdi.util.MXException;
import psdi.mbo.MboSet;
import psdi.app.inventory.virtual.KitRemote;
import psdi.util.MXApplicationException;
import psdi.app.location.LocationRemote;
import psdi.mbo.StatefulMbo;

public class Inventory extends StatefulMbo implements InventoryRemote
{
    public boolean isKitComponentToAddToStore;
    private boolean autoCreateInvBalances;
    private boolean autoCreateInvCost;
    private boolean autoCreateInvLifoFifoCost;
    LocationRemote loc;
    private MXApplicationException mxe;
    private int relationshipIndicator;
    public boolean doneRemoveExtra;
    private boolean itemIsLotted;
    private String makeOrBreak;
    private String kitCostMethod;
    private double kitCost;
    private KitRemote kit;
    private double defaultIssueCost;
    private long kitTransID;
    private String kitTransAttr;
    double accumulativeTotalCurBal;
    
    public Date exchageDate = null;	///AMB<======
    
    public Inventory(final MboSet ms) throws MXException, RemoteException {
        super(ms);
        this.isKitComponentToAddToStore = false;
        this.autoCreateInvBalances = true;
        this.autoCreateInvCost = true;
        this.autoCreateInvLifoFifoCost = true;
        this.loc = null;
        this.mxe = null;
        this.relationshipIndicator = 0;
        this.doneRemoveExtra = false;
        this.itemIsLotted = false;
        this.makeOrBreak = null;
        this.kitCostMethod = null;
        this.kitCost = 0.0;
        this.kit = null;
        this.defaultIssueCost = 0.0;
        this.kitTransID = 0L;
        this.kitTransAttr = "";
        this.accumulativeTotalCurBal = -999.99;
    }
    
    public String getProcess() {
        return "INVENTORY";
    }
    
    @Override
    protected MboSetRemote getStatusHistory() throws MXException, RemoteException {
        return this.getMboSet("INVSTATUS");
    }
    
    @Override
    protected StatusHandler getStatusHandler() {
        return new InvStatusHandler(this);
    }
    
    @Override
    public String getStatusListName() {
        return "ITEMSTATUS";
    }
    
    @Override
    public void setIsKitComponentToAddToStore(final boolean isKitComponentToAddToStore) throws MXException, RemoteException {
        this.isKitComponentToAddToStore = isKitComponentToAddToStore;
    }
    
    @Override
    public void init() throws MXException {
        super.init();
        try {
            final String[] alwaysReadOnly = { "avgcost", "lastcost", "lastissuedate", "issueytd", "holdingbal", "issue1yrago", "issue2yrago", "issue3yrago", "avblbalance", "curbaltotal", "physcnttotal", "ReservedQty", "rqtynotstaged", "stagedQty", "shippedQty", "ExpiredQty", "status", "hardreservedqty", "softreservedqty", "HARDQTYSHIPPED" };
            this.setFieldFlag(alwaysReadOnly, 7L, true);
            if (!this.toBeAdded()) {
                this.setFieldFlag("costtype", 7L, true);
                if (this.getBoolean("consignment")) {
                    this.setFieldFlag("internal", 7L, true);
                }
                if (this.getBoolean("internal")) {
                    this.setFieldFlag("storelocsiteid", 7L, false);
                    this.setFieldFlag("storeloc", 7L, false);
                    this.setFieldFlag("vendor", 7L, true);
                }
                else {
                    this.setFieldFlag("storelocsiteid", 7L, true);
                    this.setFieldFlag("storeloc", 7L, true);
                    this.setFieldFlag("vendor", 7L, false);
                }
                final MboRemote owner = this.getOwner();
                if (owner != null && owner.getOwner() != null && !owner.getOwner().isBasedOn("InvUse") && !owner.getOwner().isBasedOn("InvUseLine")) {
                    final MboSetRemote locSet = this.getMboSet("LOCATIONS");
                    this.loc = (LocationRemote)locSet.getMbo(0);
                    if (this.loc != null) {
                        try {
                            this.loc.authorizeUserStore(this.getUserInfo().getUserName());
                        }
                        catch (MXApplicationException ex) {
                            this.mxe = ex;
                            this.setFlag(7L, true);
                            locSet.close();
                            return;
                        }
                    }
                    locSet.close();
                }
                final String[] sometimesReadOnly = { "itemnum", "itemsetid", "location", "stdcost", "curbal" };
                this.setFieldFlag(sometimesReadOnly, 7L, true);
                if (owner != null && owner instanceof ItemRemote) {
                    this.setFieldFlag("category", 7L, true);
                    this.setFieldFlag("binnum", 7L, true);
                }
                final String costtype = this.getCostType();
                if (costtype.equalsIgnoreCase("LIFO") || costtype.equalsIgnoreCase("FIFO")) {
                    final MboSetRemote invLifoFifoCostSet = this.getMboSet("INVLIFOFIFOCOST_COND");
                    if (invLifoFifoCostSet.count() > 0) {
                        this.setValue("lifofifocost", this.getAverageCost(invLifoFifoCostSet), 2L);
                    }
                }
                if (this.isConsignment()) {
                    this.setFieldFlag("vendor", 7L, true);
                    this.setFieldFlag("manufacturer", 7L, true);
                    this.setFieldFlag("modelnum", 7L, true);
                    this.setFieldFlag("catalogcode", 7L, true);
                }
            }
        }
        catch (RemoteException ex2) {}
    }
    
    @Override
    public void initFieldFlagsOnMbo(final String attrName) throws MXException {
        super.initFieldFlagsOnMbo(attrName);
        try {
            if (attrName.equalsIgnoreCase("invgentype") && !this.toBeAdded() && this.getInvGenType() != null && !this.getInvGenType().equalsIgnoreCase("FREQUENCY")) {
                this.setValueNull("frequency", 11L);
                this.setValueNull("frequnit", 11L);
                this.setValueNull("nextinvoicedate", 11L);
                this.setFieldFlag("frequency", 7L, true);
                this.setFieldFlag("frequnit", 7L, true);
                this.setFieldFlag("nextinvoicedate", 7L, true);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void add() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner instanceof ItemRemote) {
            this.setValue("itemnum", owner.getString("itemnum"), 2L);
            this.setValue("itemsetid", owner.getString("itemsetid"), 2L);
            final ItemOrgInfoSetRemote itemOrgInfoSet = (ItemOrgInfoSetRemote)this.getMboSet("ITEMORGINFO");
            if (!itemOrgInfoSet.isEmpty()) {
                this.setValue("status", itemOrgInfoSet.getMbo(0).getString("status"), 2L);
                if (!itemOrgInfoSet.getMbo(0).isNull("RECEIPTTOLERANCE")) {
                    this.setValue("RECEIPTTOLERANCE", itemOrgInfoSet.getMbo(0).getDouble("RECEIPTTOLERANCE"), 11L);
                }
            }
            else if (!owner.isNull("RECEIPTTOLERANCE")) {
                this.setValue("RECEIPTTOLERANCE", owner.getDouble("RECEIPTTOLERANCE"), 11L);
            }
        }
        else {
            this.setValue("status", this.getTranslator().toExternalDefaultValue("ITEMSTATUS", "ACTIVE", this), 2L);
        }
        this.setValue("avgcost", 0, 2L);
        this.setValue("lastcost", 0, 2L);
        this.setValue("issueytd", 0, 2L);
        this.setValue("issue1yrago", 0, 2L);
        this.setValue("issue2yrago", 0, 2L);
        this.setValue("issue3yrago", 0, 2L);
        this.setValue("avblbalance", 0, 2L);
        this.setValue("curbaltotal", 0, 2L);
        this.setValue("physcnttotal", 0, 2L);
        this.setValue("ReservedQty", 0, 2L);
        this.setValue("ExpiredQty", 0, 2L);
        final String category = this.getMboValue("category").getMboValueInfo().getDefaultValue();
        if (category != null) {
            this.setValue("CATEGORY", category, 2L);
        }
        else {
            this.setValue("CATEGORY", this.getTranslator().toExternalDefaultValue("CATEGORY", "STK", this), 2L);
        }
        this.setFieldFlag("conditioncode", 7L, !this.isConditionEnabled());
        this.setValue("statusdate", MXServer.getMXServer().getDate(), 2L);
        this.setValue("reorder", true, 2L);
        this.setCostType();
    }
    
    @Override
    public void setCostType() throws MXException, RemoteException {
        final String assetCost = this.getMboServer().getMaxVar().getString("COSTFROMASSET", this.getOrgSiteForMaxvar("COSTFROMASSET"));
        final MboRemote item = this.getMboSet("ITEM").getMbo(0);
        if (assetCost.equals("1") && item != null && ((ItemRemote)item).isRotating()) {
            this.setValue("costtype", this.getTranslator().toExternalDefaultValue("COSTTYPE", "ASSET", this), 2L);
        }
        else {
            final String costType = this.getMboServer().getMaxVar().getString("DEFISSUECOST", this.getOrgSiteForMaxvar("DEFISSUECOST"));
            if (costType.equals("AVGCOST")) {
                this.setValue("costtype", this.getTranslator().toExternalDefaultValue("COSTTYPE", "AVERAGE", this), 2L);
            }
            else if (costType.equals("STDCOST")) {
                this.setValue("costtype", this.getTranslator().toExternalDefaultValue("COSTTYPE", "STANDARD", this), 2L);
            }
            else if (costType.equals("LIFO")) {
                this.setValue("costtype", this.getTranslator().toExternalDefaultValue("COSTTYPE", "LIFO", this), 2L);
            }
            else if (costType.equals("FIFO")) {
                this.setValue("costtype", this.getTranslator().toExternalDefaultValue("COSTTYPE", "FIFO", this), 2L);
            }
        }
    }
    
    @Override
    public void appValidate() throws MXException, RemoteException {
        super.appValidate();
        if (this.toBeAdded() && this.isNull("conditioncode") && this.isConditionEnabled()) {
            final Object[] param = { this.getString("itemnum") };
            throw new MXApplicationException("inventory", "noConditionCode", param);
        }
        if (this.getBoolean("internal") && (this.getString("storelocsiteid").equals("") || this.getString("storeloc").equals(""))) {
            throw new MXApplicationException("inventory", "cannotIssueNoStoreloc");
        }
        final double orderqty = this.getMboValue("orderqty").getDouble();
        if (MXMath.compareTo(orderqty, 0.0) < 1) {
            throw new MXApplicationException("inventory", "invalidOrderQty");
        }
        if (this.isConsignment() && !this.isNull("consvendor")) {
            this.setValue("vendor", this.getString("consvendor"), 2L);
        }
        if (this.isConsignment() && this.getBoolean("internal")) {
            throw new MXApplicationException("inventory", "PrimVendorNotAllowedForConsStoreroom");
        }
    }
    
    protected boolean isConditionEnabled() throws MXException, RemoteException {
        if (this.isNull("itemnum")) {
            return false;
        }
        final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
        return item != null && item.isConditionEnabled();
    }
    
    @Override
    public boolean isKit() throws MXException, RemoteException {
        if (this.isNull("itemnum")) {
            return false;
        }
        final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
        return item != null && item.isKit();
    }
    
    @Override
    public boolean isCapitalized() throws MXException, RemoteException {
        if (this.isNull("itemnum")) {
            return false;
        }
        final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
        return item != null && item.isCapitalized();
    }
    
    protected MboRemote addInvBalanceAndRelatedObjects(final double curbal) throws MXException, RemoteException {
        final String costtype = this.getCostType();
        MboRemote invLifoFifoCost = null;
        if (this.autoCreateInvLifoFifoCost && (costtype.equalsIgnoreCase("LIFO") || costtype.equalsIgnoreCase("FIFO"))) {
            invLifoFifoCost = this.addInvLifoFifoCostRecord(this.getString("conditioncode"));
        }
        else if (this.autoCreateInvCost && this.getMboSet("INVCOST").isEmpty()) {
            final MboRemote newinvcost = this.addInvCostRecord(this.getString("conditioncode"));
        }
        InvBalancesRemote invBal = null;
        final MboSetRemote invBalSet = this.getInvBalanceSet(null, null, this.getString("conditioncode"));
        boolean invBalanceCreated = true;
        if (this.autoCreateInvBalances && (invBalSet == null || invBalSet.isEmpty())) {
            invBal = this.addInvBalancesRecord(this.getString("binnum"), this.getString("lotnum"), curbal, this.getString("conditionCode"));
            if (invBal == null) {
                invBalanceCreated = false;
            }
        }
        if (invBal == null) {
            invBal = (InvBalancesRemote)invBalSet.getMbo(0);
        }
        final MboRemote invTrans = this.addInsertItemTrans(invBal);
        if (invLifoFifoCost != null && invTrans != null) {
            invLifoFifoCost.setValue("refobject", "INVTRANS", 2L);
            invLifoFifoCost.setValue("refobjectid", invTrans.getLong("invtransid"), 2L);
        }
        return invBal;
    }
    
    public void save() throws MXException, RemoteException {
        if (this.toBeAdded()) {
            if (this.getDouble("unitcost") > 0.0) {
                this.setValue("stdcost", this.getDouble("unitcost"), 2L);
            }
            this.addInvBalanceAndRelatedObjects(this.getDouble("curbal"));
        }
        else if (this.getMboValue("binnum").isModified() && this.getInvBalanceSet(this.getString("binnum"), this.getString("lotnum")).isEmpty()) {
            this.addInvBalancesRecord(this.getString("binnum"), this.getString("lotnum"), this.getDouble("curbal"));
        }
        if (!this.isNull("vendor")) {
            this.addUpdateInvVendor();
        }
        final MboSetRemote mutSet = this.getInstanciatedMboSet("MATUSETRANS");
        super.save();
        if (mutSet == null) {
            return;
        }
        if (mutSet.hasWarnings()) {
            final MboSet thisSet = (MboSet)this.getThisMboSet();
            thisSet.clearWarnings();
            final MXException[] a = mutSet.getWarnings();
            for (int i = 0; i < a.length; ++i) {
                thisSet.addWarning(a[i]);
            }
        }
    }
    
    @Override
    public void addUpdateInvVendor() throws MXException, RemoteException {
        if (this.getMboValue("vendor").isModified() || this.getMboValue("manufacturer").isModified() || this.getMboValue("modelnum").isModified() || this.getMboValue("catalogcode").isModified()) {
            final MboSetRemote invVendorSet = this.getMboSet("INVVENDOR");
            final MboRemote primaryVendor = this.getPrimaryInvVendor(invVendorSet);
            if (primaryVendor == null) {
                final MboRemote newInvVendor = this.getMboSet("INVVENDOR").add();
                newInvVendor.setValue("itemnum", this.getString("itemnum"), 2L);
                newInvVendor.setValue("itemsetid", this.getString("itemsetid"), 2L);
                newInvVendor.setValue("vendor", this.getString("vendor"), 2L);
                newInvVendor.setValue("catalogcode", this.getString("catalogcode"), 2L);
                newInvVendor.setValue("manufacturer", this.getString("manufacturer"), 2L);
                newInvVendor.setValue("modelnum", this.getString("modelnum"), 2L);
                newInvVendor.setValue("conditioncode", this.getString("conditioncode"), 2L);
                newInvVendor.setValue("siteid", this.getString("siteid"), 2L);
                newInvVendor.setValue("lastdate", MXServer.getMXServer().getDate(), 2L);
                if (this.isConsignment()) {
                    newInvVendor.setValue("lastcost", this.getDouble("unitcost"), 2L);
                }
            }
            else if (this.getMboValue("catalogcode").isModified()) {
                primaryVendor.setValue("catalogcode", this.getString("catalogcode"), 2L);
            }
        }
    }
    
    public MboRemote getPrimaryInvVendor(final MboSetRemote invVendSet) throws MXException, RemoteException {
        int index = 0;
        MboRemote primaryVendor = null;
        while ((primaryVendor = invVendSet.getMbo(index)) != null) {
            if (this.getString("itemnum").equals(primaryVendor.getString("itemnum")) && this.getString("itemsetid").equals(primaryVendor.getString("itemsetid")) && this.getString("vendor").equals(primaryVendor.getString("vendor")) && this.getString("manufacturer").equals(primaryVendor.getString("manufacturer")) && this.getString("catalogcode").equals(primaryVendor.getString("catalogcode")) && this.getString("siteid").equals(primaryVendor.getString("siteid")) && this.getString("conditioncode").equals(primaryVendor.getString("conditioncode")) && this.getString("modelnum").equals(primaryVendor.getString("modelnum"))) {
                return primaryVendor;
            }
            ++index;
        }
        return null;
    }
    
    public MboRemote doAdjustment(final NonPersistentMboRemote invAdj) throws MXException, RemoteException {
        final String internalTransType = this.getTranslator().toInternalString("ITTYPE", invAdj.getString("adjustmenttype"), invAdj);
        MboRemote invTrans = null;
        if (internalTransType.equals("AVGCSTADJ")) {
            if (invAdj.isNull("newcost")) {
                throw new MXApplicationException("inventory", "adjAvgCostNoCost");
            }
            invTrans = this.adjustAverageCost(invAdj.getDouble("newcost"), invAdj.getString("conditionCode"));
            invTrans.setValue("gldebitacct", invAdj.getString("controlacc"), 2L);
            invTrans.setValue("glcreditacct", invAdj.getString("invcostadjacc"), 2L);
        }
        else if (internalTransType.equals("CURBALADJ")) {
            if (invAdj.isNull("quantity")) {
                throw new MXApplicationException("inventory", "adjCurBalNoBalance");
            }
            invTrans = this.adjustCurrentBalance(invAdj.getString("binnum"), invAdj.getString("lotnum"), invAdj.getDouble("quantity"), invAdj.getString("conditioncode"));
            invTrans.setValue("gldebitacct", invAdj.getString("controlacc"), 2L);
            invTrans.setValue("glcreditacct", invAdj.getString("shrinkageacc"), 2L);
        }
        else if (internalTransType.equals("PCOUNTADJ")) {
            if (invAdj.isNull("quantity")) {
                throw new MXApplicationException("inventory", "adjPhyCntNoNewCount");
            }
            invTrans = this.adjustPhysicalCount(invAdj.getString("binnum"), invAdj.getString("lotnum"), invAdj.getDouble("quantity"), invAdj.getDate("adjustmentDate"));
        }
        else if (internalTransType.equals("RECBALADJ")) {
            this.reconcileBalances(invAdj.getString("controlacc"), invAdj.getString("shrinkageacc"), invAdj.getString("memo"));
        }
        else if (internalTransType.equals("STDCSTADJ")) {
            if (invAdj.isNull("newcost")) {
                throw new MXApplicationException("inventory", "adjStdCostNoCost");
            }
            invTrans = this.adjustStandardCost(invAdj.getDouble("newcost"), invAdj.getString("conditioncode"));
            invTrans.setValue("gldebitacct", invAdj.getString("controlacc"), 2L);
            invTrans.setValue("glcreditacct", invAdj.getString("invcostadjacc"), 2L);
        }
        if (invTrans != null) {
            invTrans.setValue("memo", invAdj.getString("memo"), 2L);
        }
        return invTrans;
    }
    
    @Override
    public MboRemote adjustCurrentBalance(final String binnum, final String lotnum, final double newBalance, final String conditionCode) throws MXException, RemoteException {
        final InvBalances bal = this.getInvBalanceRecord(binnum, lotnum, conditionCode);
        if (bal == null) {
            throw new MXApplicationException("inventory", "noBalanceRecord");
        }
        return bal.adjustCurrentBalance(newBalance);
    }
    
    @Override
    public MboRemote adjustPhysicalCount(final String binnum, final String lotnum, final double quantity, final Date pCountDate) throws MXException, RemoteException {
        return this.adjustPhysicalCount(binnum, lotnum, quantity, pCountDate, null, null);
    }
    
    @Override
    public MboRemote adjustPhysicalCount(final String binnum, final String lotnum, final double quantity, final Date pCountDate, final String ownersysid) throws MXException, RemoteException {
        return this.adjustPhysicalCount(binnum, lotnum, quantity, pCountDate, ownersysid, null);
    }
    
    public MboRemote adjustPhysicalCount(final String binnum, final String lotnum, final double quantity, final Date pCountDate, final String ownersysid, final String conditionCode) throws MXException, RemoteException {
        final InvBalances bal = this.getInvBalanceRecord(binnum, lotnum, conditionCode);
        if (bal == null) {
            throw new MXApplicationException("inventory", "noBalanceRecord");
        }
        final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
        final boolean useIntegration = intserv.useIntegrationRules(ownersysid, this.getString("ownersysid"), "INVPHY", this.getUserInfo());
        MboRemote rtn = null;
        if (!useIntegration) {
            rtn = bal.adjustPhysicalCount(quantity, pCountDate);
            rtn.setValue("ownersysid", ownersysid, 11L);
        }
        return rtn;
    }
    
    public double getDefaultIssueCost(final AssetRemote assetRemote, final String conditionCode) throws MXException, RemoteException {
        double value = 0.0;
        final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
        if (item != null) {
            if (item.getBoolean("capitalized")) {
                return value;
            }
            if (item.isRotating()) {
                final String costType = this.getCostType();
                if (costType.equalsIgnoreCase("ASSET")) {
                    value = assetRemote.getDouble("invcost");
                }
                else {
                    value = this.getDefaultIssueCost(conditionCode);
                }
            }
            else {
                value = this.getDefaultIssueCost(conditionCode);
            }
        }
        return value;
    }
    
    @Override
    public double getDefaultIssueCost(final AssetRemote assetRemote) throws MXException, RemoteException {
        return this.getDefaultIssueCost(assetRemote, null);
    }
    
    @Override
    public double getDefaultIssueCost() throws MXException, RemoteException {
        final String conditionCode = null;
        return this.getDefaultIssueCost(conditionCode);
    }
    
    @Override
    public double getDefaultIssueCost(final String conditionCode) throws MXException, RemoteException {
        final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
        if (item != null && item.getBoolean("capitalized")) {
            return 0.0;
        }
        if (item != null) {
            item.getThisMboSet().cleanup();
        }
        final String costType = this.getCostType();
        if (costType.equalsIgnoreCase("LIFO") || costType.equalsIgnoreCase("FIFO")) {
            final MboRemote owner = this.getOwner();
            final MboRemote issueCurrentItem = this.getMboSet("ISSUECURRENTITEM").getMbo(0);
            if ((owner != null && ((owner.isBasedOn("InvUseLine") && ((InvUseLine)owner).isReturn()) || (owner.isBasedOn("MatUseTrans") && ((MatUseTrans)owner).isReturn()))) || (issueCurrentItem != null && this.getTranslator().toInternalString("ISSUETYP", issueCurrentItem.getString("ISSUETYPE")).equalsIgnoreCase("RETURN"))) {
                final MboSetRemote invlifofifocostset = this.getInvLifoFifoCostRecordSetSortedASC();
                if (invlifofifocostset.isEmpty()) {
                    return 0.0;
                }
                return ((InvLifoFifoCostSet)invlifofifocostset).getDefaultIssueCostNewCost();
            }
            else {
                final MboSetRemote invlifofifocostset = this.getInvLifoFifoCostRecordSet(conditionCode);
                if (invlifofifocostset.isEmpty()) {
                    return 0.0;
                }
                return ((InvLifoFifoCostSet)invlifofifocostset).getDefaultIssueCost();
            }
        }
        else {
            final MboRemote invcost = this.getInvCostRecord(conditionCode);
            if (invcost == null) {
                return 0.0;
            }
            return ((InvCost)invcost).getDefaultIssueCost();
        }
    }
    
    public double getDefaultIssueCost(final ArrayList<InvLifoFifoCost> invLifoFifoCostList) throws MXException, RemoteException {
        if (invLifoFifoCostList.isEmpty()) {
            return 0.0;
        }
        return invLifoFifoCostList.get(0).getDouble("unitcost");
    }
    
    @Override
    public void updateInventoryAverageCost(final double quantity, final double totalvalue) throws MXException, RemoteException {
        this.updateInventoryAverageCost(quantity, totalvalue, 1.0);
    }
    
    @Override
    public void updateInventoryAverageCost(final double quantity, final double totalvalue, final double exr) throws MXException, RemoteException {
        this.updateInventoryAverageCost(quantity, totalvalue, exr, "");
    }
    
    public void updateInventoryAverageCost(final double quantity, final double totalvalue, final double exr, InvCost invCost) throws MXException, RemoteException {
        if (invCost == null) {
            invCost = (InvCost)this.getInvCostRecord();
        }
        if (invCost == null) {
            throw new MXApplicationException("inventory", "invCostNotFound");
        }
        invCost.updateAverageCost(quantity, totalvalue, exr);
    }
    
    public void updateInventoryAverageCost(final double quantity, final double totalvalue, final double exr, final String conditionCode) throws MXException, RemoteException {
        final InvCost invC = (InvCost)this.getInvCostRecord(conditionCode);
        if (invC == null) {
            throw new MXApplicationException("inventory", "invCostNotFound");
        }
        invC.updateAverageCost(quantity, totalvalue, exr);
    }
    
    public MboRemote getInvCostRecord(final String conditionCode) throws MXException, RemoteException {
        if (conditionCode == null || conditionCode.equals("")) {
            return this.getInvCostRecord();
        }
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location = :location and siteid = :siteid and itemsetid = :itemsetid and conditioncode = :1");
        sqf.setObject(1, "INVCOST", "CONDITIONCODE", conditionCode);
        return this.getMboSet("$InvCost" + this.getString("siteid") + this.getString("itemnum") + this.getString("location") + conditionCode + this.getString("itemsetid"), "INVCOST", sqf.format()).getMbo(0);
    }
    
    MboRemote getInvCostRecord() throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location = :location and siteid = :siteid and itemsetid = :itemsetid and condrate=100");
        return this.getMboSet("$InvCost100Percent" + this.getString("siteid") + this.getString("itemnum") + this.getString("location") + this.getString("itemsetid"), "INVCOST", sqf.format()).getMbo(0);
    }
    
    MboSetRemote getAllInvCostRecords() throws MXException, RemoteException {
        return this.getMboSet("INVCOST");
    }
    
    MboRemote getInvCostRecordInTheSet(final String conditionCode) throws MXException, RemoteException {
        final MboSetRemote invCostSet = this.getMboSet("INVCOST");
        MboRemote invcost = null;
        for (int index = 0; (invcost = invCostSet.getMbo(index)) != null; ++index) {
            if (invcost.getString("conditioncode").equals(conditionCode)) {
                return invcost;
            }
        }
        return null;
    }
    
    MboRemote getInvBalancesInTheSet(final String conditionCode) throws MXException, RemoteException {
        final MboSetRemote invBalSet = this.getMboSet("INVBALANCES");
        MboRemote invbal = null;
        for (int index = 0; (invbal = invBalSet.getMbo(index)) != null; ++index) {
            if (invbal.getString("conditioncode").equals(conditionCode)) {
                return invbal;
            }
        }
        return null;
    }
    
    MboRemote addInvCostRecord() throws MXException, RemoteException {
        return this.addInvCostRecord(null);
    }
    
    MboRemote addInvCostRecord(final String conditioncode) throws MXException, RemoteException {
        final MboRemote invcost = this.getMboSet("INVCOST").addAtEnd();
        if (conditioncode != null && !conditioncode.equals("")) {
            invcost.setValue("conditioncode", conditioncode, 2L);
        }
        return invcost;
    }
    
    @Override
    public MboRemote addInvLifoFifoCostRecord(final String conditioncode) throws MXException, RemoteException {
        final MboRemote invlifofifo = this.getMboSet("INVLIFOFIFOCOST").addAtEnd(2L);
        invlifofifo.setValue("conditioncode", conditioncode, 2L);
        return invlifofifo;
    }
    
    @Override
    public void updateInventoryIssueDetails(final Date issDate, final double quantity) throws MXException, RemoteException {
        this.setValue("lastissuedate", issDate, 2L);
        this.setValue("issueytd", this.getDouble("issueytd") - quantity, 2L);
    }
    
    @Override
    public void updateInventoryLastCost(final double unitcost) throws MXException, RemoteException {
        this.updateInventoryLastCost(unitcost, 1.0);
    }
    
    @Override
    public void updateInventoryLastCost(final double unitcost, final double exr) throws MXException, RemoteException {
        this.updateInventoryLastCost(unitcost, exr, null);
    }
    
    @Override
    public void updateInventoryLastCost(final double unitcost, final double exr, InvCost invcost) throws MXException, RemoteException {
        final double value = unitcost * exr;
        if (value < 0.0) {
            throw new MXApplicationException("inventory", "negValueNotAllowed");
        }
        if (invcost == null) {
            invcost = (InvCost)this.getInvCostRecord();
        }
        if (invcost == null) {
            throw new MXApplicationException("inventory", "negValueNotAllowed");
        }
        invcost.updateLastCost(value);
    }
    
    @Override
    public void updateInventoryDeliveryTime(final double deliveryTime) throws MXException, RemoteException {
        this.setValue("deliverytime", deliveryTime, 2L);
    }
    
    @Override
    public MboRemote adjustAverageCost(final double newcost, final String conditionCode) throws MXException, RemoteException {
        if (newcost < 0.0) {
            throw new MXApplicationException("inventory", "negValueNotAllowed");
        }
        if (this.getBoolean("item.capitalized")) {
            throw new MXApplicationException("inventory", "adjAvgCostItemCap");
        }
        final InvCost invcost = (InvCost)this.getInvCostRecord(conditionCode);
        if (invcost == null) {
            throw new MXApplicationException("inventory", "adjAvgCostItemCap");
        }
        return invcost.adjustAverageCost(newcost);
    }
    
    @Override
    public void changeStockCategory(final String newcat) throws MXException, RemoteException {
        if (!newcat.equals(this.getString("category"))) {
            this.setValue("category", newcat, 2L);
        }
    }
    
    @Override
    public MboRemote adjustStandardCost(final double newcost, final String conditionCode) throws MXException, RemoteException {
        if (newcost < 0.0) {
            throw new MXApplicationException("inventory", "negValueNotAllowed");
        }
        if (this.getBoolean("item.capitalized")) {
            throw new MXApplicationException("inventory", "adjStdCostItemCap");
        }
        final InvCost invcost = (InvCost)this.getInvCostRecord(conditionCode);
        if (invcost == null) {
            throw new MXApplicationException("inventory", "adjStdCostItemCap");
        }
        return invcost.adjustStandardCost(newcost);
    }
    
    @Override
    public double getDefaultAverageCost() throws MXException, RemoteException {
        final MboRemote invcost = this.getInvCostRecord();
        if (invcost != null) {
            return invcost.getDouble("avgcost");
        }
        return 0.0;
    }
    
    @Override
    public void zeroYTDQuantities() throws MXException, RemoteException {
        this.setValue("issue3yrago", this.getDouble("issue2yrago"), 2L);
        this.setValue("issue2yrago", this.getDouble("issue1yrago"), 2L);
        this.setValue("issue1yrago", this.getDouble("issueytd"), 2L);
        this.setValue("issueytd", 0.0, 2L);
    }
    
    @Override
    public void reconcileBalances() throws MXException, RemoteException {
        this.reconcileBalances(null, null, null);
    }
    
    @Override
    public void reconcileBalances(final String controlacc, final String shrinkageacc, final String remark) throws MXException, RemoteException {
        final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
        final MboSetRemote balSet = this.getMboSet("INVBALANCES_NOREC");
        final String costtype = this.getCostType();
        MboRemote balMbo = null;
        MboRemote invTrans = null;
        for (int i = 0; (balMbo = balSet.getMbo(i)) != null; ++i) {
            final boolean useIntegration = intserv.useIntegrationRules(balMbo.getString("ownersysid"), this.getString("ownersysid"), "INVPHY", this.getUserInfo());
            if (!useIntegration) {
                if (costtype.equalsIgnoreCase("LIFO") || costtype.equalsIgnoreCase("FIFO")) {
                    ((InvBalances)balMbo).reconcileBalancesLifoFifo(controlacc, shrinkageacc, remark);
                    if (!balMbo.getBoolean("reconciled")) {
                        invTrans = ((InvBalances)balMbo).reconcileBalances();
                        if (controlacc != null && !controlacc.equals("")) {
                            invTrans.setValue("gldebitacct", controlacc, 2L);
                        }
                        if (shrinkageacc != null && !shrinkageacc.equals("")) {
                            invTrans.setValue("glcreditacct", shrinkageacc, 2L);
                        }
                        if (remark != null && !remark.equals("")) {
                            invTrans.setValue("memo", remark, 2L);
                        }
                        final MboRemote invLifoFifoCost = this.getMboSet("INVLIFOFIFOCOST").getMbo(0);
                        if (invLifoFifoCost != null) {
                            invLifoFifoCost.setValue("refobject", "INVTRANS", 2L);
                            invLifoFifoCost.setValue("refobjectid", invTrans.getLong("invtransid"), 2L);
                        }
                    }
                }
                else {
                    invTrans = ((InvBalances)balMbo).reconcileBalances();
                    if (controlacc != null && !controlacc.equals("")) {
                        invTrans.setValue("gldebitacct", controlacc, 2L);
                    }
                    if (shrinkageacc != null && !shrinkageacc.equals("")) {
                        invTrans.setValue("glcreditacct", shrinkageacc, 2L);
                    }
                    if (remark != null && !remark.equals("")) {
                        invTrans.setValue("memo", remark, 2L);
                    }
                }
            }
        }
    }
    
    @Override
    public MboRemote reconcileBalances(final String binnum, final String lotnum) throws MXException, RemoteException {
        final InvBalances bal = this.getInvBalanceRecord(binnum, lotnum);
        if (bal == null) {
            throw new MXApplicationException("inventory", "noBalanceRecord");
        }
        final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
        final boolean useIntegration = intserv.useIntegrationRules(bal.getString("ownersysid"), this.getString("ownersysid"), "INVPHY", this.getUserInfo());
        MboRemote rtn = null;
        if (!useIntegration) {
            rtn = bal.reconcileBalances();
        }
        return rtn;
    }
    
    @Override
    public double getCurrentBalance(final String binnum, final String lotnum) throws MXException, RemoteException {
        return this.getCurrentBalance(binnum, lotnum, null);
    }
    
    @Override
    public double getCurrentBalance(final String binnum, final String lotnum, final String conditionCode) throws MXException, RemoteException {
        final MboSetRemote invBalSet = this.getInvBalanceSet(binnum, lotnum, conditionCode);
        if (invBalSet == null || invBalSet.isEmpty()) {
            return 0.0;
        }
        return invBalSet.sum("curbal");
    }
    
    @Override
    public double getPhysicalCount(final String binnum, final String lotnum) throws MXException, RemoteException {
        return this.getPhysicalCount(binnum, lotnum, null);
    }
    
    public double getPhysicalCount(final String binnum, final String lotnum, final String conditionCode) throws MXException, RemoteException {
        double rtn = 0.0;
        final MboSetRemote invBalSet = this.getInvBalanceSet(binnum, lotnum, conditionCode);
        if (invBalSet.isEmpty()) {
            return 0.0;
        }
        rtn = invBalSet.sum("physcnt");
        return rtn;
    }
    
    @Override
    public InvBalancesRemote addInvBalancesRecord(final String binnum, final String lotnum, final double curbal) throws MXException, RemoteException {
        return this.addInvBalancesRecord(binnum, lotnum, curbal, null);
    }
    
    @Override
    public InvBalancesRemote addInvBalancesRecord(String binnum, String lotnum, final double curbal, String conditionCode) throws MXException, RemoteException {
        if ((lotnum == null || lotnum.equals("")) && curbal == 0.0) {
            return null;
        }
        if (binnum == null) {
            binnum = "";
        }
        if (lotnum == null) {
            lotnum = "";
        }
        if (conditionCode == null) {
            conditionCode = "";
        }
        final InvBalancesSet invBalSet = (InvBalancesSet)this.getInvBalanceSet(binnum, lotnum, conditionCode);
        if (!invBalSet.isEmpty()) {
            throw new MXApplicationException("inventory", "invbalAlreadyExists");
        }
        final InvBalancesRemote newInvBal = (InvBalancesRemote)invBalSet.add();
        newInvBal.setValue("binnum", binnum, 2L);
        if (!lotnum.equals("")) {
            newInvBal.setValue("lotnum", lotnum, 2L);
        }
        newInvBal.updateCurrentBalance(curbal);
        newInvBal.setValue("physcnt", curbal, 2L);
        newInvBal.setValue("conditioncode", conditionCode, 2L);
        return newInvBal;
    }
    
    @Override
    public void canDelete() throws MXException, RemoteException {
        final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
        final boolean useIntegration = intserv.useIntegrationRules("THISMX", this.getString("ownersysid"), "INVDEL", this.getUserInfo());
        if (useIntegration) {
            return;
        }
        MboSetRemote toBeKilled = null;
        final MXServer server = MXServer.getMXServer();
        StringBuffer where = null;
        try {
            final SqlFormat sLocation = new SqlFormat(this, "itemnum = :itemnum and location = :location and itemsetid = :itemsetid");
            final SqlFormat sStoreloc = new SqlFormat(this, "itemnum = :itemnum and (( storeloc = '') or (storeloc=:location)) and itemsetid = :itemsetid and siteid=:siteid");
            toBeKilled = this.getMboSet("$jobPlanSetI", "JOBMATERIAL", sLocation.format());
            if (!toBeKilled.isEmpty()) {
                throw new MXApplicationException("inventory", "noDeleteInvJobPlan");
            }
            toBeKilled.reset();
            toBeKilled = this.getMboSet("$invResSetI", "INVRESERVE", sLocation.format());
            if (!toBeKilled.isEmpty()) {
                throw new MXApplicationException("inventory", "noDeleteInvRes");
            }
            toBeKilled.reset();
            final AppServiceRemote woLookup = (AppServiceRemote)server.lookup("WORKORDER");
            final String woOpenCriteria = woLookup.getCriteria("OPEN");
            where = new StringBuffer();
            where.append(sLocation.format());
            where.append(" and exists (select workorder.wonum from workorder where workorder.wonum=wpmaterial.wonum and " + woOpenCriteria + ")");
            toBeKilled = this.getMboSet("$woSetI", "WPMATERIAL", where.toString());
            if (!toBeKilled.isEmpty()) {
                throw new MXApplicationException("inventory", "noDeleteWPMat");
            }
            toBeKilled.reset();
            where = new StringBuffer(sStoreloc.format());
            where.append(" and exists (select mr.mrnum from mr where mr.mrnum=mrline.mrnum and mr.historyflag=:no)");
            toBeKilled = this.getMboSet("$mrLineSetI", "MRLINE", where.toString());
            if (!toBeKilled.isEmpty()) {
                throw new MXApplicationException("inventory", "noDeleteMRLine");
            }
            toBeKilled.reset();
            where = new StringBuffer(sStoreloc.format());
            where.append(" and exists (select pr.prnum from pr where pr.prnum=prline.prnum and pr.historyflag=:no)");
            toBeKilled = this.getMboSet("$prLineSetI", "PRLINE", where.toString());
            if (!toBeKilled.isEmpty()) {
                throw new MXApplicationException("inventory", "noDeletePRLine");
            }
            toBeKilled.reset();
            where = new StringBuffer(sStoreloc.format());
            where.append(" and exists (select po.ponum from po where po.ponum=poline.ponum and po.historyflag=:no)");
            toBeKilled = this.getMboSet("$poLineSetI", "POLINE", where.toString());
            if (!toBeKilled.isEmpty()) {
                throw new MXApplicationException("inventory", "noDeleteP0Line");
            }
            toBeKilled.reset();
            final InvBalancesSet invBalSet = (InvBalancesSet)this.getMboSet("INVBALANCES");
            for (int size = invBalSet.getSize(), i = 0; i < size; ++i) {
                ((InvBalances)invBalSet.getMbo(i)).canDelete();
            }
        }
        finally {
            toBeKilled.reset();
            toBeKilled = null;
        }
    }
    
    @Override
    public void delete(final long access) throws MXException, RemoteException {
        this.getMboSet("INVBALANCES").deleteAll();
        this.getMboSet("INVCOST").deleteAll();
        super.delete(access);
    }
    
    InvBalances getInvBalanceRecord(final String binnum, final String lotnum) throws MXException, RemoteException {
        return this.getInvBalanceRecord(binnum, lotnum, null);
    }
    
    public InvBalances getInvBalanceRecord(final String binnum, final String lotnum, final String conditionCode) throws MXException, RemoteException {
        return this.getInvBalanceRecord(binnum, lotnum, conditionCode, null, null);
    }
    
    public InvBalances getInvBalanceRecord(String binnum, String lotnum, String conditionCode, String storeLoc, String storeSite) throws MXException, RemoteException {
        if (binnum == null) {
            binnum = "";
        }
        if (lotnum == null) {
            lotnum = "";
        }
        if (conditionCode == null) {
            conditionCode = "";
        }
        if (storeLoc == null) {
            storeLoc = "";
        }
        if (storeSite == null) {
            storeSite = "";
        }
        final MboSetRemote invBalSet = this.getInvBalanceSet(binnum, lotnum, conditionCode, storeLoc, storeSite);
        return (InvBalances)invBalSet.getMbo(0);
    }
    
    @Override
    public MboSetRemote getInvBalancesSetForKitComponent(String binnum) throws MXException, RemoteException {
        if (binnum.equals("")) {
            binnum = null;
        }
        return this.getInvBalanceSet(binnum, null, null);
    }
    
    MboSetRemote getInvBalanceSet(final String binnum, final String lotnum) throws MXException, RemoteException {
        return this.getInvBalanceSet(binnum, lotnum, null);
    }
    
    MboSetRemote getInvBalanceSet(final String binnum, final String lotnum, final String conditionCode) throws MXException, RemoteException {
        return this.getInvBalanceSet(binnum, lotnum, conditionCode, null, null);
    }
    
    MboSetRemote getInvBalanceSet(final String binnum, final String lotnum, final String conditionCode, final String storeLoc, final String storeSite) throws MXException, RemoteException {
        if (binnum == null && lotnum == null && conditionCode == null) {
            return this.getMboSet("INVBALANCES");
        }
        final StringBuffer where = new StringBuffer();
        if (storeLoc == null || storeLoc.equals("")) {
            SqlFormat sqf00 = new SqlFormat(this, "itemnum = :itemnum and location = :location and siteid = :siteid and itemsetid = :itemsetid");
            where.append(sqf00.format());
            sqf00 = null;
        }
        else {
            SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location = :1 and siteid = :2 and itemsetid = :itemsetid");
            sqf.setObject(1, "INVBALANCES", "LOCATION", storeLoc);
            sqf.setObject(2, "INVBALANCES", "SITEID", storeSite);
            where.append(sqf.format());
            sqf = null;
        }
        if (binnum != null) {
            if (binnum.equals("")) {
                where.append(" and binnum is null");
            }
            else {
                SqlFormat sqf2 = new SqlFormat(this, " and binnum = :1");
                sqf2.setObject(1, "INVBALANCES", "BINNUM", binnum);
                where.append(sqf2.format());
                sqf2 = null;
            }
        }
        if (lotnum != null) {
            if (lotnum.equals("")) {
                where.append(" and lotnum is null");
            }
            else {
                SqlFormat sqf3 = new SqlFormat(this, " and lotnum = :1");
                sqf3.setObject(1, "INVBALANCES", "LOTNUM", lotnum);
                where.append(sqf3.format());
                sqf3 = null;
            }
        }
        if (conditionCode != null) {
            if (conditionCode.equals("")) {
                where.append(" and conditioncode is null ");
            }
            else {
                SqlFormat sqf4 = new SqlFormat(this, " and conditioncode = :1");
                sqf4.setObject(1, "INVBALANCES", "conditioncode", conditionCode);
                where.append(sqf4.format());
                sqf4 = null;
            }
        }
        final MboSetRemote invBalSet = this.getMboSet("$InvBalance" + this.getString("itemnum") + this.getString("location") + binnum + lotnum + conditionCode + this.getString("itemsetid") + this.relationshipIndicator, "INVBALANCES", where.toString());
        return invBalSet;
    }
    
    protected void triggerNewRelationship(final int relationshipIndicator) throws MXException, RemoteException {
        this.relationshipIndicator = relationshipIndicator;
    }
    
    @Override
    public MboRemote createIssue() throws MXException, RemoteException {
        MboRemote rtn = null;
        final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
        final boolean useIntegration = intserv.useIntegrationRules("THISMX", this.getString("ownersysid"), "INVISS", this.getUserInfo());
        if (!useIntegration) {
            rtn = this.generateIssueRecord(null);
            return rtn;
        }
        throw new MXApplicationException("inventory", "mxcollabINVISS");
    }
    
    protected MboRemote createIssue(final MboSetRemote issueSet, final String ownersysid1) throws MXException, RemoteException {
        MboRemote rtn = null;
        final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
        if (ownersysid1 != null) {
            final boolean useIntegration = intserv.useIntegrationRules(ownersysid1, this.getString("ownersysid"), "INVISS", this.getUserInfo());
            if (useIntegration) {
                throw new MXApplicationException("inventory", "mxcollabINVISS");
            }
            rtn = this.generateIssueRecord(issueSet);
        }
        return rtn;
    }
    
    protected MboRemote createMiscReceipt(final MboSetRemote existingReceiptSet, final String ownersysid) throws MXException, RemoteException {
        final IntegrationServiceRemote intserv = (IntegrationServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("INTEGRATION");
        final boolean useintegration = intserv.useIntegrationRules(ownersysid, this.getString("ownersysid"), "INVISSR", this.getUserInfo());
        if (useintegration) {
            throw new MXApplicationException("inventory", "mxcollabINVISSR");
        }
        MboSetRemote receiptSet = null;
        if (existingReceiptSet != null) {
            receiptSet = existingReceiptSet;
        }
        else {
            receiptSet = this.getMboSet("MATRECTRANS");
        }
        return receiptSet.add();
    }
    
    @Override
    public boolean isStocked() throws MXException, RemoteException {
        String category = "";
        final MboSetRemote itemorgset = this.getMboSet("itemorginfo");
        if (itemorgset != null) {
            final MboRemote itemorginfo = itemorgset.getMbo(0);
            if (itemorginfo != null) {
                final Translate tr = this.getTranslator();
                category = tr.toInternalString("CATEGORY", itemorginfo.getString("category"));
            }
            itemorgset.close();
        }
        return category.equalsIgnoreCase("STK");
    }
    
    @Override
    public boolean isNonStocked() throws MXException, RemoteException {
        String category = "";
        final MboSetRemote itemorgset = this.getMboSet("itemorginfo");
        if (itemorgset != null) {
            final MboRemote itemorginfo = itemorgset.getMbo(0);
            if (itemorginfo != null) {
                final Translate tr = this.getTranslator();
                category = tr.toInternalString("CATEGORY", itemorginfo.getString("category"));
            }
            itemorgset.close();
        }
        return category.equalsIgnoreCase("NS");
    }
    
    @Override
    public boolean isSpecialOrder() throws MXException, RemoteException {
        String category = "";
        final MboSetRemote itemorgset = this.getMboSet("itemorginfo");
        if (itemorgset != null) {
            final MboRemote itemorginfo = itemorgset.getMbo(0);
            if (itemorginfo != null) {
                final Translate tr = this.getTranslator();
                category = tr.toInternalString("CATEGORY", itemorginfo.getString("category"));
            }
            itemorgset.close();
        }
        return category.equalsIgnoreCase("SP");
    }
    
    @Override
    public void changeCapitalizedStatus(final boolean capitalized) throws MXException, RemoteException {
        this.changeCapitalizedStatus(capitalized, "", "");
    }
    
    @Override
    public void changeCapitalizedStatus(final boolean capitalized, final String capitalacc, final String memo) throws MXException, RemoteException {
        final String[] readonly = { "avgcost", "lastcost", "stdcost", "shrinkage", "costadjacc", "controlacc" };
        if (capitalized) {
            this.setValue("avgcost", 0, 2L);
            this.setValue("stdcost", 0, 2L);
            this.setValue("lastcost", 0, 2L);
            this.setValue("controlacc", capitalacc, 2L);
            this.setValueNull("shrinkageacc", 2L);
            this.setValueNull("INVCOSTADJACC", 2L);
            this.setFieldFlag(readonly, 7L, true);
        }
        else {
            this.setFieldFlag(readonly, 7L, false);
            final MboRemote locMbo = this.getMboSet("LOCATIONS").getMbo(0);
            this.setValue("shrinkageacc", locMbo.getString("shrinkageacc"), 2L);
            this.setValue("invcostadjacc", locMbo.getString("invcostadjacc"), 2L);
            this.setValue("controlacc", locMbo.getString("controlacc"), 2L);
        }
        final MboSetRemote invcostSet = this.getAllInvCostRecords();
        InvCost invcost = null;
        for (int mboIndex = 0; (invcost = (InvCost)invcostSet.getMbo(mboIndex)) != null; ++mboIndex) {
            invcost.changeCapitalizedStatus(capitalized, capitalacc, memo, this);
        }
    }
    
    private MboRemote generateIssueRecord(MboSetRemote issueSet) throws MXException, RemoteException {
        if (issueSet == null) {
            issueSet = this.getMboSet("$createdIssuesForMEA", "MATUSETRANS", "1=2");
        }
        final MboRemote rtn = issueSet.add();
        rtn.setValue("itemnum", this.getString("itemnum"), 2L);
        rtn.setValue("itemsetid", this.getString("itemsetid"), 2L);
        rtn.setValue("storeloc", this.getString("location"), 2L);
        rtn.setValue("siteid", this.getString("siteid"), 2L);
        return rtn;
    }
    
    private InvTransRemote addInsertItemTrans(final InvBalancesRemote invBal) throws MXException, RemoteException {
        final InvTransRemote itMbo = (InvTransRemote)this.getMboSet("INVTRANS").add(2L);
        final Translate tr = this.getTranslator();
        itMbo.setValue("TRANSTYPE", tr.toExternalDefaultValue("ITTYPE", "INSERTITEM", itMbo));
        itMbo.setValue("itemnum", this.getString("itemnum"), 11L);
        itMbo.setValue("itemsetid", this.getString("itemsetid"), 11L);
        itMbo.setValue("conditioncode", this.getString("conditioncode"), 11L);
        itMbo.setValue("storeloc", this.getString("location"), 11L);
        itMbo.setValue("binnum", this.getString("binnum"), 11L);
        itMbo.setValue("lotnum", this.getString("lotnum"), 11L);
        itMbo.setValue("curbal", this.getDouble("curbal"), 2L);
        itMbo.setValue("quantity", this.getDouble("curbal"), 11L);
        itMbo.setValue("newcost", this.getDouble("stdcost"), 11L);
        itMbo.setValue("physcnt", this.getDouble("curbal"), 2L);
        itMbo.setValue("gldebitacct", this.getString("controlacc"), 2L);
        itMbo.setValue("glcreditacct", this.getString("controlacc"), 2L);
        return itMbo;
    }
    
    protected double calculateHoldingBalance() throws MXException, RemoteException {
        final double quantity = this.getMboSet("HOLDINGBALANCE").sum("quantity");
        final double inspectedQty = this.getMboSet("HOLDINGBALANCE").sum("inspectedqty");
        return quantity - inspectedQty;
    }
    
    protected double calculateCurrentBalance() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof ItemRemote && owner.toBeAdded()) {
            return 0.0;
        }
        return this.getMboSet("INVBALANCES").sum("curbal");
    }
    
    protected double calculateReservedQty() throws MXException, RemoteException {
        return this.getMboSet("INVRESERVE").sum("reservedqty");
    }
    
    public double calculateRQtyNotStaged() throws MXException, RemoteException {
        return this.getMboSet("hardreservations").sum("reservedqty") - this.getMboSet("hardreservations").sum("stagedqty");
    }
    
    public double calculateStagedQty() throws MXException, RemoteException {
        return this.getMboSet("INVBALANCES").sum("stagedcurbal");
    }
    
    public double calculateShippedQty() throws MXException, RemoteException {
        return this.getMboSet("SHIPPEDINVUSELINE").sum("quantity");
    }
    
    public double calculateHardShippedQty() throws MXException, RemoteException {
        final MboSet set = (MboSet)this.getMboSet("hardreservations");
        final double total = set.sum("shippedqty") - set.sum("ACTUALQTY");
        set.cleanup();
        return total;
    }
    
    protected double calculateExpiredQty() throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, " itemnum = :itemnum and location = :location and itemsetid = :itemsetid and exists (select lotnum from invlot where invbalances.itemnum=invlot.itemnum and invbalances.location=invlot.location and invbalances.siteid=invlot.siteid and invbalances.itemsetid=invlot.itemsetid and invbalances.lotnum=invlot.lotnum and invlot.useby < :1)");
        sqf.setObject(1, "INVLOT", "USEBY", MXFormat.dateToString(MXServer.getMXServer().getDate(), this.getUserInfo().getLocale(), this.getUserInfo().getTimeZone()));
        final MboSetRemote invBalances = this.getMboSet("$invbalances" + this.getString("Itemnum") + this.getString("itemsetid"), "INVBALANCES", sqf.format());
        if (invBalances.isEmpty()) {
            return 0.0;
        }
        return invBalances.sum("curbal");
    }
    
    @Override
    public double calculateAvailableQty() throws MXException, RemoteException {
        return this.calculateCurrentBalance() - this.calculateRQtyNotStaged() - this.calculateExpiredQty() + this.calculateHardShippedQty();
    }
    
    public void setAutoCreateInvCost(final boolean flag) throws MXException, RemoteException {
        this.autoCreateInvCost = flag;
    }
    
    public void setAutoCreateInvBalances(final boolean flag) throws MXException, RemoteException {
        this.autoCreateInvBalances = flag;
    }
    
    @Override
    public void canTransferCurrentItem() throws MXException, RemoteException {
        if (((ItemRemote)this.getMboSet("ITEM").getMbo(0)).isRotating()) {
            throw new MXApplicationException("inventory", "dontTransferRotItem");
        }
        final String status = this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status"));
        if (status.equalsIgnoreCase("OBSOLETE") || status.equalsIgnoreCase("PENDING") || status.equalsIgnoreCase("PLANNING")) {
            final Object[] param = { this.getString("itemnum"), status };
            throw new MXApplicationException("inventory", "ActionNotAllowedStatus", param);
        }
    }
    
    @Override
    public void canIssueCurrentItem() throws MXException, RemoteException {
        final String status = this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status"));
        if (status.equalsIgnoreCase("OBSOLETE") || status.equalsIgnoreCase("PENDING") || status.equalsIgnoreCase("PLANNING")) {
            final Object[] param = { this.getString("itemnum"), status };
            throw new MXApplicationException("inventory", "ActionNotAllowedStatus", param);
        }
    }
    
    @Override
    public String getInventoryStatus() throws MXException, RemoteException {
        return this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status"));
    }
    
    @Override
    public void canAdjustStdCost() throws MXException, RemoteException {
        if (this.isCapitalized()) {
            throw new MXApplicationException("inventory", "adjStdCostItemCap");
        }
    }
    
    @Override
    public void canAdjustAvgCost() throws MXException, RemoteException {
        if (this.isCapitalized()) {
            throw new MXApplicationException("inventory", "adjAvgCostItemCap");
        }
    }
    
    @Override
    public void canAdjustBalance() throws MXException, RemoteException {
        final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
        if (item.getBoolean("rotating")) {
            throw new MXApplicationException("inventory", "adjCurBalItemRotating");
        }
    }
    
    @Override
    public void canReconcileBalances() throws MXException, RemoteException {
        final InvBalancesSet invBalSet = (InvBalancesSet)this.getMboSet("INVBALANCES_NOREC");
        if (invBalSet.isEmpty()) {
            throw new MXApplicationException("inventory", "invbalCannotReconcile");
        }
    }
    
    @Override
    public MboRemote updateCurrentBalance(final String binnum, final String lotnum, final String conditionCode, final double toIncrement) throws MXException, RemoteException {
        final InvBalances bal = this.getInvBalanceRecord(binnum, lotnum, conditionCode);
        if (bal == null) {
            throw new MXApplicationException("inventory", "noBalanceRecord");
        }
        bal.updateCurrentBalance(bal.getDouble("curbal") + toIncrement);
        return bal;
    }
    
    protected void isLotted(final boolean lotted) throws MXException, RemoteException {
        this.itemIsLotted = lotted;
    }
    
    protected void setRequiredFieldsForCurBal() throws MXException, RemoteException {
        if (this.itemIsLotted && (this.getMboValue("curbal").isModified() || this.getMboValue("lotnum").isModified())) {
            this.setFieldFlag("binnum", 128L, true);
        }
    }
    
    @Override
    public MXApplicationException getErrorMsg() throws RemoteException {
        return this.mxe;
    }
    
    @Override
    public void canDoAction() throws MXException, RemoteException {
        if (this.mxe != null) {
            throw this.mxe;
        }
    }
    
    @Override
    public MboSetRemote siteReceiptsAndTransfers() throws MXException, RemoteException {
        final ProfileRemote profile = this.getProfile();
        final String loginSite = profile.getInsertSite();
        final SqlFormat sqf = new SqlFormat(this, " itemnum = :itemnum and itemsetid = :itemsetid and ( (siteid=:1 and tostoreloc=:location) or (fromsiteid=:1 AND FROMSTORELOC=:location) )");
        sqf.setObject(1, "SITE", "SITEID", loginSite);
        final MboSetRemote msr = this.getMboSet("$receiptsAndTransfers" + this.getString("Itemnum") + "_" + this.getString("Location") + "_" + this.getString("itemsetid"), "MATRECTRANS");
        msr.setWhere(sqf.format());
        msr.reset();
        if (!msr.isEmpty()) {
            return msr;
        }
        return null;
    }
    
    @Override
    public void setKitAction(final String makeOrBreak) throws MXException, RemoteException {
        this.makeOrBreak = makeOrBreak;
    }
    
    @Override
    public String getKitAction() throws MXException, RemoteException {
        return this.makeOrBreak;
    }
    
    @Override
    public void kitMakeOrBreak(final KitRemote kit) throws MXException, RemoteException {
        this.kitCostMethod = this.getCostType();
        this.kit = kit;
        this.defaultIssueCost = this.getDefaultIssueCost();
        if (this.makeOrBreak.equalsIgnoreCase("KITMAKE")) {
            this.kitMake(kit);
            this.kitBalanceIncrement(kit);
            this.kitMakeKitInventoryUpdate(kit);
        }
        else if (this.makeOrBreak.equalsIgnoreCase("KITBREAK")) {
            this.kitBreak(kit);
            this.kitBalanceDecrement(kit);
        }
        this.kitHandleCostVariance();
    }
    
    protected void kitHandleCostVariance() throws MXException, RemoteException {
        if (this.kitCostMethod.equalsIgnoreCase("AVERAGE") && this.makeOrBreak.equalsIgnoreCase("KITMAKE")) {
            return;
        }
        if (Math.abs(this.kitCost - this.defaultIssueCost) > 1.0E-7) {
            this.kitWriteCostVarianceTransaction();
        }
    }
    
    private void kitWriteCostVarianceTransaction() throws MXException, RemoteException {
        final InvTransSetRemote invTransSet = (InvTransSetRemote)this.getMboSet("$Create" + this.makeOrBreak + "InvTrans" + this.getString("itemnum"), "INVTRANS", "");
        final InvTrans invTrans = (InvTrans)invTransSet.add();
        invTrans.setValue("itemnum", this.kit.getString("itemnum"), 2L);
        invTrans.setValue("storeloc", this.getString("location"), 2L);
        final String transType = this.getTranslator().toExternalDefaultValue("ITTYPE", "KITCOSTVAR", this);
        invTrans.setValue("transtype", transType, 2L);
        invTrans.setValue(this.kitTransAttr, this.kitTransID, 2L);
        invTrans.setValue("quantity", 0, 2L);
        invTrans.setValue("curbal", 0, 2L);
        invTrans.setValue("physcnt", 0, 2L);
        invTrans.setValue("oldcost", this.defaultIssueCost, 2L);
        invTrans.setValue("newcost", this.defaultIssueCost, 2L);
        invTrans.setValue("gldebitacct", this.getInvCostRecord().getString("controlacc"), 2L);
        this.loc = (LocationRemote)this.getMboSet("LOCATIONS").getMbo(0);
        if (this.loc != null) {
            invTrans.setValue("glcreditacct", this.loc.getString("receiptvaracc"), 2L);
        }
        invTrans.setValue("linecost", this.kit.getDouble("quantity") * (this.defaultIssueCost - this.kitCost), 2L);
    }
    
    private void kitMake(final KitRemote kit) throws MXException, RemoteException {
        final ItemStructSetRemote itemStructKitSet = (ItemStructSetRemote)kit.getMboSet("FIRSTLEVELKITSTRUCT");
        MboRemote usageComponent = null;
        MboRemote receiptComponent = null;
        final MatRecTrans matrec = (MatRecTrans)this.getMboSet("$emptyMatRecTrans", "MATRECTRANS", "1=2").add();
        this.kitTransAttr = "MATRECTRANSID";
        this.kitTransID = matrec.getLong(this.kitTransAttr);
        this.kitCostMethod = this.getCostType();
        for (int j = 0; (usageComponent = itemStructKitSet.getMbo(j)) != null; ++j) {
            if (usageComponent.getString("itemnum").equalsIgnoreCase(usageComponent.getString("itemid"))) {
                receiptComponent = usageComponent;
            }
            else {
                final MatUseTrans matuse = (MatUseTrans)this.getMboSet("$emptyMatUseTrans", "MATUSETRANS", "1=2").add();
                matuse.setValue("MATRECTRANSID", matrec.getLong("matrectransid"), 11L);
                this.kitDoMatUseTransaction((ItemStructRemote)usageComponent, matuse);
            }
        }
        this.kitDoMatRecTransaction((ItemStructRemote)receiptComponent, kit, matrec);
    }
    
    private void kitBreak(final KitRemote kit) throws MXException, RemoteException {
        final ItemStructSetRemote itemStructKitSet = (ItemStructSetRemote)kit.getMboSet("FIRSTLEVELKITSTRUCT");
        MboRemote kitComponent = null;
        MboRemote usageComponent = null;
        final MatUseTrans matuse = (MatUseTrans)this.getMboSet("$emptyMatUseTrans", "MATUSETRANS", "1=2").add();
        this.kitTransAttr = "MATUSETRANSID";
        this.kitTransID = matuse.getLong(this.kitTransAttr);
        for (int j = 0; (kitComponent = itemStructKitSet.getMbo(j)) != null; ++j) {
            if (kitComponent.getString("itemnum").equalsIgnoreCase(kitComponent.getString("itemid"))) {
                usageComponent = kitComponent;
                break;
            }
        }
        this.kitDoMatUseTransaction((ItemStructRemote)usageComponent, matuse);
        for (int j = 0; (kitComponent = itemStructKitSet.getMbo(j)) != null; ++j) {
            if (!kitComponent.getString("itemnum").equalsIgnoreCase(kitComponent.getString("itemid"))) {
                final MatRecTrans matrec = (MatRecTrans)this.getMboSet("$emptyMatRecTrans", "MATRECTRANS", "1=2").add();
                matrec.setValue("MATUSETRANSID", matuse.getString("matusetransid"), 11L);
                this.kitDoMatRecTransaction((ItemStructRemote)kitComponent, kit, matrec);
            }
        }
    }
    
    private void kitCostAddition(final ItemStructRemote kitComponent, final Inventory invComponent) throws MXException, RemoteException {
        final InvCost invcost = (InvCost)invComponent.getInvCostRecord();
        this.kitCost += kitComponent.getDouble("quantity") * invcost.getDefaultIssueCost();
    }
    
    private void kitMakeKitInventoryUpdate(final KitRemote kit) throws MXException, RemoteException {
        final InvCost invcost = (InvCost)this.getInvCostRecord();
        this.updateInventoryAverageCost(kit.getDouble("quantity"), kit.getDouble("quantity") * this.kitCost, 1.0, invcost);
        this.updateInventoryLastCost(this.kitCost, 1.0, invcost);
    }
    
    private void kitBalanceUpdate(final KitRemote kit) throws MXException, RemoteException {
        if (this.makeOrBreak.equalsIgnoreCase("KITMAKE")) {
            this.kitBalanceIncrement(kit);
        }
        else if (this.makeOrBreak.equalsIgnoreCase("KITBREAK")) {
            this.kitBalanceDecrement(kit);
        }
    }
    
    private void kitBalanceIncrement(final KitRemote kit) throws MXException, RemoteException {
        final double kitQuantity = kit.getDouble("quantity");
        InvBalancesRemote invBalRelated = null;
        String toInvDefaultBinnum = null;
        if (!this.isNull("binnum")) {
            toInvDefaultBinnum = this.getString("binnum");
        }
        final MboSetRemote invBalSet = this.getInvBalanceSet(toInvDefaultBinnum, null, null);
        if (!invBalSet.isEmpty()) {
            invBalRelated = (InvBalances)invBalSet.getMbo(0);
        }
        boolean updateInventory = false;
        final String updateInvSetting = this.getMboServer().getMaxVar().getString("UPDATEINVENTORY", this.getOrgSiteForMaxvar("UPDATEINVENTORY"));
        if (updateInvSetting.equals("1")) {
            updateInventory = true;
        }
        if (invBalRelated != null) {
            final double toOldBal = invBalRelated.getDouble("curbal");
            invBalRelated.updateCurrentBalance(kitQuantity + toOldBal);
        }
        else {
            invBalRelated = this.addInvBalancesRecord(toInvDefaultBinnum, null, kitQuantity, null);
            if (invBalRelated != null) {
                invBalRelated.updateCurrentBalance(kitQuantity);
                invBalRelated.setValue("RECONCILED", true, 2L);
            }
        }
    }
    
    private void kitBalanceDecrement(final KitRemote kit) throws MXException, RemoteException {
        final double kitQtyToDisassemble = kit.getDouble("quantity");
        final MboSetRemote invBalancesForBreakKit = this.getMboSet("$getKitBalance" + this.getString("Itemnum") + "_" + this.getString("Location") + "_" + this.getString("itemsetid"), "INVBALANCES", "itemnum=:itemnum and itemsetid=:itemsetid and location=:location and siteid=:siteid");
        MboRemote fromInvBal = null;
        invBalancesForBreakKit.isEmpty();
        int x;
        String fromInvDefaultBinnum;
        for (x = 0, fromInvDefaultBinnum = this.getString("binnum"); (fromInvBal = invBalancesForBreakKit.getMbo(x)) != null && !fromInvBal.getString("binnum").equalsIgnoreCase(fromInvDefaultBinnum); ++x) {}
        x = 0;
        if (fromInvBal == null) {
            fromInvBal = invBalancesForBreakKit.getMbo(x);
            ++x;
        }
        double invBalCurBal = 0.0;
        double qtyFulfilled = 0.0;
        do {
            if (fromInvBal.getString("binnum").equalsIgnoreCase(fromInvDefaultBinnum) && x > 0) {
                ++x;
            }
            else {
                double qtyToTakeFromThisInvCurBal = 0.0;
                invBalCurBal = fromInvBal.getDouble("curbal");
                if (invBalCurBal >= kitQtyToDisassemble - qtyFulfilled) {
                    qtyToTakeFromThisInvCurBal = kitQtyToDisassemble - qtyFulfilled;
                }
                else {
                    qtyToTakeFromThisInvCurBal = invBalCurBal;
                }
                double oldBalance = 0.0;
                final boolean useInitialCurBal = false;
                if (useInitialCurBal) {
                    oldBalance = fromInvBal.getMboInitialValue("curbal").asDouble();
                }
                else {
                    oldBalance = fromInvBal.getDouble("curbal");
                }
                ((InvBalances)fromInvBal).updateCurrentBalance(qtyToTakeFromThisInvCurBal * -1.0 + oldBalance);
                qtyFulfilled += qtyToTakeFromThisInvCurBal;
                ++x;
            }
        } while ((fromInvBal = invBalancesForBreakKit.getMbo(x)) != null && qtyFulfilled < kitQtyToDisassemble);
    }
    
    private void kitDoMatRecTransaction(final ItemStructRemote kitComponent, final KitRemote kit, final MatRecTrans matrec) throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :1 and location = :location and itemsetid = :itemsetid and siteid = :siteid");
        sqf.setObject(1, "ITEM", "ITEMNUM", kitComponent.getString("itemnum"));
        final MboRemote componentInInventory = this.getMboSet("$getKitComponentBalance" + kitComponent.getString("Itemnum") + "_" + this.getString("Location") + "_" + this.getString("itemsetid"), "INVENTORY", sqf.format()).getMbo(0);
        try {
            final String kitMatRecTransType = this.getTranslator().toExternalDefaultValue("ISSUETYP", this.makeOrBreak, matrec);
            matrec.setValue("issuetype", kitMatRecTransType, 11L);
            matrec.setValue("itemnum", kitComponent.getString("itemnum"), 11L);
            String toBinValue = this.getString("binnum");
            String toStore = this.getString("location");
            String fromStore = componentInInventory.getString("location");
            String toSite = this.getString("siteid");
            String fromSite = componentInInventory.getString("siteid");
            double qty = kit.getDouble("quantity");
            if (this.makeOrBreak.equalsIgnoreCase("KITBREAK")) {
                toBinValue = componentInInventory.getString("binnum");
                toStore = componentInInventory.getString("location");
                fromStore = this.getString("location");
                toSite = componentInInventory.getString("siteid");
                fromSite = this.getString("siteid");
                qty = kitComponent.getDouble("kitquantity");
            }
            matrec.setValue("tobin", toBinValue, 11L);
            matrec.setValue("tostoreloc", toStore, 11L);
            matrec.setValue("siteid", toSite, 11L);
            matrec.setValue("fromstoreloc", fromStore, 11L);
            matrec.setValue("fromsiteid", fromSite, 11L);
            matrec.setValue("itemsetid", componentInInventory.getString("itemsetid"), 11L);
            matrec.setValue("orgid", componentInInventory.getString("orgid"), 11L);
            matrec.setValue("quantity", qty, 11L);
            matrec.setValue("receivedunit", componentInInventory.getString("ISSUEUNIT"), 11L);
            matrec.setValue("rejectqty", 0, 11L);
            matrec.setValue("outside", false, 11L);
            matrec.setValue("issue", false, 11L);
            matrec.setValue("totalcurbal", 0, 11L);
            matrec.setValue("oldavgcost", 0, 11L);
            matrec.setValue("glcreditacct", componentInInventory.getString("controlacc"), 2L);
            matrec.setValue("gldebitacct", componentInInventory.getString("controlacc"), 2L);
            double receiptCost = ((InventoryRemote)componentInInventory).getDefaultIssueCost();
            if (this.makeOrBreak.equalsIgnoreCase("KITMAKE")) {
                receiptCost = this.kitCost;
            }
            matrec.setValue("unitcost", receiptCost, 11L);
            matrec.setValue("actualcost", receiptCost, 11L);
            matrec.setValue("linecost", matrec.getDouble("quantity") * matrec.getDouble("unitcost"), 11L);
            matrec.setValue("currencyunitcost", matrec.getDouble("unitcost") * matrec.getDouble("exchangerate"), 11L);
            matrec.setValue("currencylinecost", matrec.getDouble("linecost") * matrec.getDouble("exchangerate"), 11L);
            matrec.setValue("conversion", 1.0, 11L);
            final String itemDescription = this.getMboSet("ITEM").getMbo(0).getString("description");
            matrec.setValue("description", itemDescription, 11L);
            matrec.setValue("remark", kit.getString("memo"), 11L);
            matrec.setValue("qtyrequested", 0, 11L);
            matrec.setValue("curbal", 0, 11L);
            matrec.setValue("costinfo", true, 11L);
            if (this.makeOrBreak.equalsIgnoreCase("KITBREAK")) {
                matrec.doKitBreakUpdates(kitComponent, kit);
                this.kitCostAddition(kitComponent, (Inventory)componentInInventory);
            }
        }
        catch (MXException m) {
            matrec.delete();
            throw m;
        }
        catch (RemoteException r) {
            matrec.delete();
            throw r;
        }
    }
    
    private void kitDoMatUseTransaction(final ItemStructRemote kitComponent, final MatUseTrans matuse) throws MXException, RemoteException {
        MboRemote inventoryBeingUsed = null;
        double qty = 0.0;
        String gldebitacct = "";
        String glcreditacct = "";
        if (this.makeOrBreak.equalsIgnoreCase("KITMAKE")) {
            final SqlFormat sqf = new SqlFormat(this, "itemnum = :1 and location = :location and itemsetid = :itemsetid and siteid = :siteid");
            sqf.setObject(1, "ITEM", "ITEMNUM", kitComponent.getString("itemnum"));
            inventoryBeingUsed = this.getMboSet("$kitGetInventoryForMatUse_" + kitComponent.getString("itemnum") + "_" + this.getString("Location") + "_" + this.getString("itemsetid"), "INVENTORY", sqf.format()).getMbo(0);
            qty = kitComponent.getDouble("kitquantity") * -1.0;
            gldebitacct = this.getString("controlacc");
            glcreditacct = inventoryBeingUsed.getString("controlacc");
        }
        else if (this.makeOrBreak.equalsIgnoreCase("KITBREAK")) {
            inventoryBeingUsed = this;
            qty = this.kit.getDouble("quantity") * -1.0;
            gldebitacct = inventoryBeingUsed.getString("controlacc");
            glcreditacct = this.getString("controlacc");
        }
        try {
            if (inventoryBeingUsed != null) {
                matuse.setValue("orgid", inventoryBeingUsed.getString("orgid"), 11L);
                matuse.setValue("siteid", inventoryBeingUsed.getString("siteid"), 11L);
                matuse.setValue("itemsetid", inventoryBeingUsed.getString("itemsetid"), 11L);
                matuse.setValue("itemnum", inventoryBeingUsed.getString("itemnum"), 11L);
                matuse.setValue("storeloc", inventoryBeingUsed.getString("location"), 11L);
            }
            matuse.setValue("quantity", qty, 11L);
            final FinancialServiceRemote finService = (FinancialServiceRemote)((AppService)this.getMboServer()).getMXServer().lookup("FINANCIAL");
            final String financialperiod = finService.getActiveFinancialPeriod(this.getUserInfo(), matuse.getDate("transdate"), matuse.getString("orgid"));
            matuse.setValue("financialperiod", financialperiod, 11L);
            double componentDefaultIssueCost = 0.0;
            if (inventoryBeingUsed != null) {
                componentDefaultIssueCost = ((InventoryRemote)inventoryBeingUsed).getDefaultIssueCost();
            }
            matuse.setValue("unitcost", componentDefaultIssueCost, 11L);
            matuse.setValue("actualcost", componentDefaultIssueCost, 11L);
            matuse.setValue("linecost", matuse.getDouble("quantity") * matuse.getDouble("unitcost"), 11L);
            matuse.setValue("exchangerate", 1, 11L);
            matuse.setValue("currencyunitcost", matuse.getDouble("unitcost"), 11L);
            matuse.setValue("currencylinecost", matuse.getDouble("linecost"), 11L);
            matuse.setValue("conversion", 1.0, 11L);
            matuse.setValue("memo", this.kit.getString("memo"), 11L);
            matuse.setValue("outside", 0, 11L);
            final String kitMatRecTransType = this.getTranslator().toExternalDefaultValue("ISSUETYP", this.makeOrBreak, matuse);
            matuse.setValue("issuetype", kitMatRecTransType, 11L);
            matuse.setValue("glcreditacct", glcreditacct, 2L);
            matuse.setValue("gldebitacct", gldebitacct, 2L);
            final String itemDescription = this.getMboSet("ITEM").getMbo(0).getString("description");
            matuse.setValue("description", itemDescription, 11L);
            matuse.setValue("qtyrequested", MXMath.abs(qty), 11L);
            matuse.setValue("enteredastask", false, 11L);
            if (this.makeOrBreak.equalsIgnoreCase("KITMAKE")) {
                matuse.doKitMakeInvBalanceUpdates();
                this.kitCostAddition(kitComponent, (Inventory)inventoryBeingUsed);
            }
        }
        catch (MXException m) {
            matuse.delete();
            throw m;
        }
        catch (RemoteException r) {
            matuse.delete();
            throw r;
        }
    }
    
    @Override
    public void canKit() throws MXException, RemoteException {
        final Object[] err = { this.getString("itemnum") };
        if (!this.isKit()) {
            throw new MXApplicationException("inventory", "cannotManageKitBalForUnKittedItem", err);
        }
        if (this.getMboSet("FIRSTLEVELKITSTRUCT").count() < 2) {
            throw new MXApplicationException("inventory", "kitStructureNotDefined", err);
        }
        if (this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status")).equals("OBSOLETE") || this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status")).equals("PENDING") || this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status")).equals("PLANNING")) {
            final Object[] params = { this.getString("itemnum"), "inventory" };
            if (BidiUtils.isBidiEnabled()) {
                params[0] = BidiUtils.buildAndPush(this.getName(), "itemnum", (String)params[0], this.getUserInfo().getLangCode());
            }
            throw new MXApplicationException("inventory", "ActionNotAllowedInvalidStatus", params);
        }
    }
    
    @Override
    public void refreshCountDate() throws MXException, RemoteException {
        final InventorySet inventorySetRemote = (InventorySet)this.getThisMboSet();
        MboSetRemote invBalSet = inventorySetRemote.getInvbalancesSet();
        if (invBalSet == null) {
            invBalSet = this.getMboSet("INVBALANCES");
        }
        int count = 0;
        for (MboRemote invBal = invBalSet.getMbo(count); invBal != null; invBal = invBalSet.getMbo(count)) {
            invBal.setValue("ADJUSTEDPHYSCNTDATE", this.getDate("PhysCntDate"), 2L);
            ++count;
        }
    }
    
    @Override
    public void copyAssetsForIssue(final MboSetRemote assetSet, final MboRemote issueItemToAssetMbo) throws RemoteException, MXException {
        final IssueItemToAssetRemote issueItem = (IssueItemToAssetRemote)issueItemToAssetMbo;
        issueItem.copyAssetsForIssue(assetSet);
    }
    
    private MboSetRemote getSparePartSet(final String itemnum, final String itemsetid, final String assetnum, final String siteid) throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :1 and assetnum = :2 and itemsetid = :3 and siteid =:4");
        sqf.setObject(1, "SPAREPART", "itemnum", itemnum);
        sqf.setObject(2, "SPAREPART", "assetnum", assetnum);
        sqf.setObject(3, "SPAREPART", "itemsetid", itemsetid);
        sqf.setObject(4, "SPAREPART", "siteid", siteid);
        return this.getMboSet("$getSparePart" + itemnum + itemsetid + assetnum, "SPAREPART", sqf.format());
    }
    
    @Override
    public void checkRequestAgainstItemMaxIssue(final String assetnum, final double qtyRequested) throws MXException, RemoteException {
        if (assetnum == null || assetnum.equals("")) {
            return;
        }
        final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
        if (item == null || item.isNull("MaxIssue")) {
            return;
        }
        final MboSetRemote spareParts = this.getSparePartSet(this.getString("itemnum"), this.getString("itemsetid"), assetnum, this.getString("siteid"));
        double qtyAlreadyIssued = 0.0;
        if (!spareParts.isEmpty()) {
            qtyAlreadyIssued = spareParts.sum("issuedqty");
        }
        final double maxissue = item.getDouble("maxissue");
        if (qtyRequested + qtyAlreadyIssued > maxissue) {
            final Object[] params = { new Double(qtyRequested), new Double(qtyAlreadyIssued), assetnum, this.getString("itemnum"), new Double(maxissue) };
            throw new MXApplicationException("inventory", "exceedsmaxissue", params);
        }
    }
    
    @Override
    public boolean checkWOExists() throws MXException, RemoteException {
        final MboSetRemote wpItemSet = this.getMboSet("$wpitem" + this.getString("itemnum") + this.getString("location"), "WPITEM", "itemnum=:itemnum and location=:location and siteid=:siteid and itemsetid=:itemsetid");
        if (!wpItemSet.isEmpty()) {
            WPItemRemote wpItem = null;
            for (int i = 0; (wpItem = (WPItemRemote)wpItemSet.getMbo(i)) != null; ++i) {
                final WOSetRemote woSet = (WOSetRemote)wpItem.getMboSet("WORKORDER");
                if (!woSet.isEmpty()) {
                    WORemote wo = null;
                    for (int j = 0; (wo = (WORemote)woSet.getMbo(j)) != null; ++j) {
                        final String woStatus = wo.getString("status");
                        if (!this.getTranslator().toInternalString("WOSTATUS", woStatus).equals("COMP") && !this.getTranslator().toInternalString("WOSTATUS", woStatus).equals("CAN") && !this.getTranslator().toInternalString("WOSTATUS", woStatus).equals("CLOSE")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean checkInvBalancesExists() throws MXException, RemoteException {
        final MboSetRemote invBalSet = this.getMboSet("INVBALANCES");
        if (!invBalSet.isEmpty()) {
            final String internalItemStatus = this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status"));
            if (internalItemStatus.equalsIgnoreCase("PENDOBS")) {
                MboRemote tempInvBal = null;
                boolean allInvBalancesAreZero = true;
                int i = 0;
                while ((tempInvBal = invBalSet.getMbo(i)) != null) {
                    ++i;
                    if (tempInvBal.getDouble("curbal") != 0.0) {
                        allInvBalancesAreZero = false;
                        break;
                    }
                }
                if (allInvBalancesAreZero) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    
    @Override
    public boolean checkAssetExists() throws MXException, RemoteException {
        final MboSetRemote assetSet = this.getMboSet("ASSET");
        return !assetSet.isEmpty();
    }
    
    @Override
    public boolean checkJPExists() throws MXException, RemoteException {
        final MboSetRemote jobItemSet = this.getMboSet("$jobitem" + this.getString("itemnum") + this.getString("location"), "JOBITEM", "itemnum=:itemnum and location=:location and siteid=:siteid and itemsetid=:itemsetid");
        if (!jobItemSet.isEmpty()) {
            JobItemRemote jobItem = null;
            for (int i = 0; (jobItem = (JobItemRemote)jobItemSet.getMbo(i)) != null; ++i) {
                final JobPlanSetRemote jobPlanSet = (JobPlanSetRemote)jobItem.getMboSet("JOBPLANREV");
                if (!jobPlanSet.isEmpty()) {
                    JobPlanRemote jobPlan = null;
                    for (int j = 0; (jobPlan = (JobPlanRemote)jobPlanSet.getMbo(j)) != null; ++j) {
                        final String woStatus = jobPlan.getString("status");
                        if (!this.getTranslator().toInternalString("JOBPLANSTATUS", woStatus).equals("INACTIVE") && !this.getTranslator().toInternalString("JOBPLANSTATUS", woStatus).equals("REVISED")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean checkMRExists() throws MXException, RemoteException {
        final MboSetRemote mrLineSet = this.getMboSet("MRLine");
        if (!mrLineSet.isEmpty()) {
            MRLineRemote mrLine = null;
            for (int i = 0; (mrLine = (MRLineRemote)mrLineSet.getMbo(i)) != null; ++i) {
                final MRSetRemote mrSet = (MRSetRemote)mrLine.getMboSet("MR");
                if (!mrSet.isEmpty()) {
                    MRRemote mr = null;
                    for (int j = 0; (mr = (MRRemote)mrSet.getMbo(j)) != null; ++j) {
                        final String mrStatus = mr.getString("status");
                        if (!this.getTranslator().toInternalString("MRSTATUS", mrStatus).equals("CLOSE") && !this.getTranslator().toInternalString("MRSTATUS", mrStatus).equals("CAN")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean checkPRExists() throws MXException, RemoteException {
        final MboSetRemote prLineSet = this.getMboSet("PRLine");
        if (!prLineSet.isEmpty()) {
            PRLineRemote prLine = null;
            for (int i = 0; (prLine = (PRLineRemote)prLineSet.getMbo(i)) != null; ++i) {
                final PRSetRemote prSet = (PRSetRemote)prLine.getMboSet("PR");
                if (!prSet.isEmpty()) {
                    PRRemote pr = null;
                    for (int j = 0; (pr = (PRRemote)prSet.getMbo(j)) != null; ++j) {
                        final String prStatus = pr.getString("status");
                        if (!this.getTranslator().toInternalString("PRSTATUS", prStatus).equals("COMP") && !this.getTranslator().toInternalString("PRSTATUS", prStatus).equals("CAN")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean checkPOExists() throws MXException, RemoteException {
        final MboSetRemote poLineSet = this.getMboSet("POLine");
        poLineSet.setQbeExactMatch(true);
        poLineSet.setQbe("storeloc", this.getString("location"));
        poLineSet.setQbe("siteid", this.getString("siteid"));
        poLineSet.reset();
        if (!poLineSet.isEmpty()) {
            POLineRemote poLine = null;
            for (int i = 0; (poLine = (POLineRemote)poLineSet.getMbo(i)) != null; ++i) {
                final POSetRemote poSet = (POSetRemote)poLine.getMboSet("PO");
                if (!poSet.isEmpty()) {
                    PORemote po = null;
                    for (int j = 0; (po = (PORemote)poSet.getMbo(j)) != null; ++j) {
                        final String poStatus = po.getString("status");
                        if (!this.getTranslator().toInternalString("POSTATUS", poStatus).equals("CLOSE") && !this.getTranslator().toInternalString("POSTATUS", poStatus).equals("CAN")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean checkContractExists() throws MXException, RemoteException {
        final MboSetRemote contractLineSet = this.getMboSet("ContractLine");
        if (!contractLineSet.isEmpty()) {
            ContractLineRemote contractLine = null;
            for (int i = 0; (contractLine = (ContractLineRemote)contractLineSet.getMbo(i)) != null; ++i) {
                final ContractSetRemote contractSet = (ContractSetRemote)contractLine.getMboSet("CONTRACT");
                if (!contractSet.isEmpty()) {
                    ContractRemote contract = null;
                    for (int j = 0; (contract = (ContractRemote)contractSet.getMbo(j)) != null; ++j) {
                        final String contractStatus = contract.getString("status");
                        if (!this.getTranslator().toInternalString("CONTRACTSTATUS", contractStatus).equals("CLOSE") && !this.getTranslator().toInternalString("CONTRACTSTATUS", contractStatus).equals("CAN") && !this.getTranslator().toInternalString("CONTRACTSTATUS", contractStatus).equals("REVISE")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean checkCIExists() throws MXException, RemoteException {
        final MboSetRemote ciSet = this.getMboSet("CI");
        return !ciSet.isEmpty();
    }
    
    @Override
    public void changeStatus(final String status, final Date date, final String memo, final long accessModifier) throws MXException, RemoteException {
        MXException caughtException = null;
        String itemLoc = this.getString("ITEMNUM") + "/" + this.getString("LOCATION");
        final String langCode = this.getUserInfo().getLangCode();
        if (BidiUtils.isBidiEnabled()) {
            itemLoc = BidiUtils.buildAndPush(this.getMboSetInfo(), "ITEMNUM", this.getString("ITEMNUM"), langCode) + BidiUtils.pushBidiString("/", langCode) + BidiUtils.buildAndPush(this.getMboSetInfo(), "LOCATION", this.getString("LOCATION"), langCode);
        }
        final Object[] params = { itemLoc, status };
        if (BidiUtils.isBidiEnabled()) {
            params[1] = BidiUtils.buildAndPush("", "", status, langCode);
        }
        try {
            this.validateChangeStatus(status, date, memo);
            super.changeStatus(status, date, memo, accessModifier);
        }
        catch (Throwable thrownObject) {
            caughtException = new MXApplicationException("item", "StatusChangeFailure", params, thrownObject);
            throw caughtException;
        }
    }
    
    @Override
    public void validateChangeStatus(final String status, final Date date, final String memo) throws MXException, RemoteException {
        final String currentMaxStatus = this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status"));
        final String desiredMaxStatus = this.getTranslator().toInternalString("ITEMSTATUS", status);
        if (!currentMaxStatus.equals(desiredMaxStatus)) {
            final InvStatusHandler invStatusHandler = (InvStatusHandler)this.getStatusHandler();
            invStatusHandler.validateChangeStatus(this.getString("Status"), status, date, memo);
        }
    }
    
    @Override
    public boolean isPendobs() throws MXException, RemoteException {
        final String curStatus = this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status"));
        return curStatus.equals("PENDOBS");
    }
    
    @Override
    public boolean isObsolete() throws MXException, RemoteException {
        final String curStatus = this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status"));
        return curStatus.equals("OBSOLETE");
    }
    
    @Override
    public boolean isPlanning() throws MXException, RemoteException {
        final String curStatus = this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status"));
        return curStatus.equals("PLANNING");
    }
    
    protected double calculatePhyscnt() throws MXException, RemoteException {
        final MboRemote owner = this.getOwner();
        if (owner != null && owner instanceof ItemRemote && owner.toBeAdded()) {
            return 0.0;
        }
        return this.getMboSet("INVBALANCES").sum("physcnt");
    }
    
    public boolean checkInvUseStatusForPlanning() throws MXException, RemoteException {
        final MboSetRemote invUseLineSet = this.getMboSet("INVUSELINE");
        int i = 0;
        InvUseLineRemote invUseLine = null;
        while ((invUseLine = (InvUseLineRemote)invUseLineSet.getMbo(i)) != null) {
            final String status = ((InvUseRemote)invUseLine.getOwner()).getStatus();
            if (status.equalsIgnoreCase("STAGED") || status.equalsIgnoreCase("SHIPPED")) {
                return true;
            }
            ++i;
        }
        return false;
    }
    
    public boolean checkInvUseStatusForPndObs() throws MXException, RemoteException {
        final MboSetRemote invUseLineSet = this.getMboSet("INVUSELINE");
        int i = 0;
        InvUseLineRemote invUseLine = null;
        while ((invUseLine = (InvUseLineRemote)invUseLineSet.getMbo(i)) != null) {
            final String status = ((InvUseRemote)invUseLine.getOwner()).getStatus();
            if (status.equalsIgnoreCase("ENTERED") || status.equalsIgnoreCase("STAGED") || status.equalsIgnoreCase("SHIPPED")) {
                return true;
            }
            ++i;
        }
        return false;
    }
    
    public String getStatus() throws MXException, RemoteException {
        final String status = this.getTranslator().toInternalString("ITEMSTATUS", this.getString("status"));
        return status;
    }
    
    @Override
    public String getCostType() throws MXException, RemoteException {
        return this.getTranslator().toInternalString("COSTTYPE", this.getString("costtype"));
    }
    
    @Override
    public MboSetRemote getInvLifoFifoCostRecordSetSorted(final String conditionCode) throws MXException, RemoteException {
        if (conditionCode == null || conditionCode.equals("")) {
            return this.getInvLifoFifoCostRecordSetSorted();
        }
        final SqlFormat sqf = new SqlFormat(this, "itemnum=:itemnum and location=:location and siteid=:siteid and itemsetid = :itemsetid and conditioncode = :1");
        sqf.setObject(1, "INVLIFOFIFOCOST", "CONDITIONCODE", conditionCode);
        final InvLifoFifoCostSetRemote invLifoFifoSet = (InvLifoFifoCostSetRemote)this.getMboSet("$InvLifoFifoCost" + this.getString("itemnum") + this.getString("location") + this.getString("siteid") + conditionCode + this.getString("itemsetid"), "INVLIFOFIFOCOST", sqf.format());
        if (this.getCostType().equals("LIFO")) {
            invLifoFifoSet.setOrderBy("costdate desc");
        }
        else {
            invLifoFifoSet.setOrderBy("costdate asc");
        }
        invLifoFifoSet.reset();
        return invLifoFifoSet;
    }
    
    MboSetRemote getInvLifoFifoCostRecordSetSorted() throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location =:location and siteid=:siteid and itemsetid = :itemsetid");
        final InvLifoFifoCostSetRemote invLifoFifoSet = (InvLifoFifoCostSetRemote)this.getMboSet("$InvLifoFifoCost" + this.getString("itemnum") + this.getString("location") + this.getString("siteid") + this.getString("itemsetid"), "INVLIFOFIFOCOST", sqf.format());
        if (this.getCostType().equals("LIFO")) {
            invLifoFifoSet.setOrderBy("costdate desc");
        }
        else {
            invLifoFifoSet.setOrderBy("costdate asc");
        }
        invLifoFifoSet.reset();
        return invLifoFifoSet;
    }
    
    @Override
    public MboSetRemote getInvLifoFifoCostRecordSet(final String conditionCode) throws MXException, RemoteException {
        if (conditionCode == null || conditionCode.equals("")) {
            return this.getInvLifoFifoCostRecordSet();
        }
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location=:location and siteid=:siteid and itemsetid = :itemsetid and conditioncode = :1");
        sqf.setObject(1, "INVLIFOFIFOCOST", "CONDITIONCODE", conditionCode);
        final InvLifoFifoCostSetRemote invLifoFifoSet = (InvLifoFifoCostSetRemote)this.getMboSet("$InvLifoFifoCost" + this.getString("itemnum") + this.getString("location") + this.getString("siteid") + conditionCode + this.getString("itemsetid"), "INVLIFOFIFOCOST", sqf.format());
        return invLifoFifoSet;
    }
    
    public MboSetRemote getInvLifoFifoCostRecordSet(final String conditionCode, final long transid) throws MXException, RemoteException {
        if (conditionCode == null || conditionCode.equals("")) {
            return this.getInvLifoFifoCostRecordSet(transid);
        }
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location=:location and siteid=:siteid and itemsetid = :itemsetid and conditioncode = :1 and refobjectid =:2");
        sqf.setObject(1, "INVLIFOFIFOCOST", "CONDITIONCODE", conditionCode);
        sqf.setObject(2, "INVLIFOFIFOCOST", "REFOBJECTID", transid + "");
        final InvLifoFifoCostSetRemote invLifoFifoSet = (InvLifoFifoCostSetRemote)this.getMboSet("$InvLifoFifoCost" + this.getString("itemnum") + this.getString("location") + this.getString("siteid") + conditionCode + this.getString("itemsetid") + transid + "", "INVLIFOFIFOCOST", sqf.format());
        return invLifoFifoSet;
    }
    
    MboSetRemote getInvLifoFifoCostRecordSet(final long transid) throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location =:location and siteid=:siteid and itemsetid = :itemsetid and refobjectid = " + transid);
        final InvLifoFifoCostSetRemote invLifoFifoSet = (InvLifoFifoCostSetRemote)this.getMboSet("$InvLifoFifoCost" + this.getString("itemnum") + this.getString("location") + this.getString("siteid") + this.getString("itemsetid") + transid, "INVLIFOFIFOCOST", sqf.format());
        return invLifoFifoSet;
    }
    
    MboSetRemote getInvLifoFifoCostRecordSetSortedASC() throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location =:location and siteid=:siteid and itemsetid = :itemsetid");
        final InvLifoFifoCostSetRemote invLifoFifoSet = (InvLifoFifoCostSetRemote)this.getMboSet("$InvLifoFifoCost" + this.getString("itemnum") + this.getString("location") + this.getString("siteid") + this.getString("itemsetid"), "INVLIFOFIFOCOST", sqf.format());
        invLifoFifoSet.setOrderBy("costdate asc");
        invLifoFifoSet.reset();
        return invLifoFifoSet;
    }
    
    MboSetRemote getInvLifoFifoCostRecordSet() throws MXException, RemoteException {
        final SqlFormat sqf = new SqlFormat(this, "itemnum = :itemnum and location =:location and siteid=:siteid and itemsetid = :itemsetid");
        final InvLifoFifoCostSetRemote invLifoFifoSet = (InvLifoFifoCostSetRemote)this.getMboSet("$InvLifoFifoCost" + this.getString("itemnum") + this.getString("location") + this.getString("siteid") + this.getString("itemsetid"), "INVLIFOFIFOCOST", sqf.format());
        return invLifoFifoSet;
    }
    
    MboRemote getInvLifoFifoCostRecordInTheSet(final String conditionCode) throws MXException, RemoteException {
        final MboSetRemote invLifoFifoCostSet = this.getMboSet("INVLIFOFIFOCOST");
        MboRemote invlifofifocost = null;
        for (int index = 0; (invlifofifocost = invLifoFifoCostSet.getMbo(index)) != null; ++index) {
            if (invlifofifocost.getString("conditioncode").equals(conditionCode)) {
                return invlifofifocost;
            }
        }
        return null;
    }
    
    ArrayList<InvLifoFifoCost> getInvLifoFifoCostRecordSetInTheSet(final String conditionCode) throws MXException, RemoteException {
        final ArrayList<InvLifoFifoCost> lifoFifoCostList = new ArrayList<InvLifoFifoCost>();
        final MboSetRemote invLifoFifoCostSet = this.getMboSet("INVLIFOFIFOCOST");
        InvLifoFifoCost invlifofifocost = null;
        int index = 0;
        if (conditionCode != null && !conditionCode.equals("")) {
            while ((invlifofifocost = (InvLifoFifoCost)invLifoFifoCostSet.getMbo(index)) != null) {
                if (invlifofifocost.getString("conditioncode").equals(conditionCode)) {
                    lifoFifoCostList.add(invlifofifocost);
                }
                ++index;
            }
        }
        else {
            while ((invlifofifocost = (InvLifoFifoCost)invLifoFifoCostSet.getMbo(index)) != null) {
                lifoFifoCostList.add(invlifofifocost);
                ++index;
            }
        }
        if (this.getCostType().equals("LIFO")) {
            Collections.sort(lifoFifoCostList, Collections.reverseOrder());
        }
        else {
            Collections.sort(lifoFifoCostList);
        }
        return lifoFifoCostList;
    }
    
    @Override
    public MboRemote addInvLifoFifoCostRecord(final double quantity, final double unitcost, final String conditioncode, final String getName, final long matrectransid) throws MXException, RemoteException {
        final MboRemote newinvLifoFiFoCost = this.addInvLifoFifoCostRecord(conditioncode);
        newinvLifoFiFoCost.setValue("quantity", quantity, 2L);
        newinvLifoFiFoCost.setValue("unitcost", unitcost, 2L);
        newinvLifoFiFoCost.setValue("refobject", getName, 2L);
        newinvLifoFiFoCost.setValue("refobjectid", matrectransid, 2L);
        return newinvLifoFiFoCost;
    }
    
    @Override
    public void consumeInvLifoFifoCostRecord(final double quantity, final String conditionCode) throws MXException, RemoteException {
        final MboSetRemote invlifofifocostset = this.getInvLifoFifoCostRecordSetSorted(conditionCode);
        double qty = MXMath.abs(quantity);
        int i = 0;
        MboRemote invlifofifocost = null;
        while ((invlifofifocost = invlifofifocostset.getMbo(i)) != null) {
            if (invlifofifocost.getDouble("quantity") == 0.0) {
                ++i;
            }
            else {
                if (invlifofifocost.getDouble("quantity") == qty) {
                    invlifofifocost.setValue("quantity", 0, 2L);
                    break;
                }
                if (invlifofifocost.getDouble("quantity") > qty) {
                    qty = MXMath.subtract(invlifofifocost.getDouble("quantity"), qty);
                    invlifofifocost.setValue("quantity", qty, 2L);
                    break;
                }
                qty = MXMath.subtract(qty, invlifofifocost.getDouble("quantity"));
                invlifofifocost.setValue("quantity", 0, 2L);
                ++i;
            }
        }
    }
    
    public void consumeInvLifoFifoCostRecord(final double quantity, final String conditionCode, final long transid) throws MXException, RemoteException {
        final MboSetRemote invlifofifocostset = this.getInvLifoFifoCostRecordSet(conditionCode, transid);
        double qty = MXMath.abs(quantity);
        int i = 0;
        double remainingqty = qty;
        MboRemote invlifofifocost = null;
        while ((invlifofifocost = invlifofifocostset.getMbo(i)) != null) {
            if (invlifofifocost.getDouble("quantity") == 0.0) {
                ++i;
            }
            else {
                if (invlifofifocost.getDouble("quantity") == qty) {
                    invlifofifocost.setValue("quantity", 0, 2L);
                    remainingqty = 0.0;
                    break;
                }
                if (invlifofifocost.getDouble("quantity") > qty) {
                    qty = MXMath.subtract(invlifofifocost.getDouble("quantity"), qty);
                    invlifofifocost.setValue("quantity", qty, 2L);
                    remainingqty = 0.0;
                    break;
                }
                qty = MXMath.subtract(qty, invlifofifocost.getDouble("quantity"));
                invlifofifocost.setValue("quantity", 0, 2L);
                remainingqty = qty;
                ++i;
            }
        }
        if (remainingqty > 0.0) {
            this.consumeInvLifoFifoCostRecord(qty, conditionCode);
        }
    }
    
    @Override
    public double getAverageCost(final MboSetRemote invLifoFifoCostSet) throws MXException, RemoteException {
        double cost = 0.0;
        int i = 0;
        InvLifoFifoCost invlifofifocost = null;
        double totalQty = 0.0;
        while ((invlifofifocost = (InvLifoFifoCost)invLifoFifoCostSet.getMbo(i)) != null) {
            cost += MXMath.multiply(invlifofifocost.getDouble("unitcost"), invlifofifocost.getDouble("quantity"));
            totalQty += invlifofifocost.getDouble("quantity");
            ++i;
        }
        if (totalQty > 0.0) {
            return MXMath.divide(cost, totalQty);
        }
        return 0.0;
    }
    
    public void setAutoCreateInvLifoFifoCost(final boolean flag) throws MXException, RemoteException {
        this.autoCreateInvLifoFifoCost = flag;
    }
    
    public boolean getAutoCreateInvLifoFifoCost() throws MXException, RemoteException {
        return this.autoCreateInvLifoFifoCost;
    }
    
    public MboRemote getInvLifoFifoCostRecord(final String conditionCode) throws MXException, RemoteException {
        final MboSetRemote invLifoFifoSet = this.getInvLifoFifoCostRecordSet(conditionCode);
        if (invLifoFifoSet.isEmpty()) {
            invLifoFifoSet.close();
            return null;
        }
        invLifoFifoSet.setOrderBy("costdate desc");
        invLifoFifoSet.reset();
        final MboRemote invLifoFifo = invLifoFifoSet.getMbo(0);
        invLifoFifoSet.close();
        return invLifoFifo;
    }
    
    @Override
    public void setNextInvoiceDate() throws MXException, RemoteException {
        final Date nextDate = this.getNextDate();
        this.setValue("NextInvoiceDate", nextDate, 2L);
    }
    
    public Date getNextDate() throws MXException, RemoteException {
        if (this.isNull("frequency") || this.getInt("frequency") == 0 || this.isNull("frequnit")) {
            return null;
        }
        final int frequency = this.getInt("frequency");
        final String freqUnit = this.getTranslator().toInternalString("FREQUNIT", this.getString("frequnit"));
        Date countFrom = null;
        countFrom = MXServer.getMXServer().getDate();
        Date retDate = null;
        if (countFrom == null) {
            return retDate;
        }
        if (freqUnit.equals("DAYS")) {
            final Calendar c = Calendar.getInstance();
            c.setTime(countFrom);
            c.add(5, frequency);
            retDate = c.getTime();
        }
        else if (freqUnit.equals("WEEKS")) {
            final Calendar c = Calendar.getInstance();
            c.setTime(countFrom);
            c.add(5, frequency * 7);
            retDate = c.getTime();
        }
        else if (freqUnit.equals("MONTHS")) {
            retDate = this.addMonths(frequency, countFrom);
        }
        else if (freqUnit.equals("YEARS")) {
            retDate = this.addYears(frequency, countFrom);
        }
        return retDate;
    }
    
    public Date addMonths(final int addMonths, final Date fromDate) throws MXException, RemoteException {
        final GregorianCalendar cm = new GregorianCalendar();
        cm.setTime(fromDate);
        final int fromDay = cm.get(5);
        cm.set(5, 1);
        cm.add(2, addMonths);
        final int resultingMonth = cm.get(2);
        cm.add(5, fromDay - 1);
        while (cm.get(2) != resultingMonth) {
            cm.add(5, -1);
        }
        return cm.getTime();
    }
    
    public Date addYears(final int addYears, final Date fromDate) {
        final GregorianCalendar cy = new GregorianCalendar();
        cy.setTime(fromDate);
        final boolean fromLeapYearDay = cy.get(5) == 29 && cy.get(2) == 2;
        cy.add(1, addYears);
        if (fromLeapYearDay && cy.get(5) != 29) {
            cy.add(5, -1);
        }
        return cy.getTime();
    }
    
    @Override
    public boolean isConsignment() throws MXException, RemoteException {
        return this.getBoolean("consignment");
    }
    
    public void updateConsignment() throws MXException, RemoteException {
        final MboSetRemote invBalset = this.getMboSet("INVBALANCES");
        final double curbal = invBalset.sum("curbal");
        if (curbal > 0.0) {
            throw new MXApplicationException("inventory", "cannotChgInvbalExists");
        }
        invBalset.reset();
        if (!this.checkReconcileFlag()) {
            throw new MXApplicationException("inventory", "cannotChgInvbalExists");
        }
        if (this.POPRExists()) {
            throw new MXApplicationException("inventory", "cannotChgPOPRExists");
        }
        if (this.isConsignment() && !this.getCostType().equalsIgnoreCase("FIFO")) {
            this.setValue("newcosttype", this.getTranslator().toExternalDefaultValue("COSTTYPE", "FIFO", this), 11L);
            this.invUpdateCostType();
        }
        if (!this.isConsignment()) {
            final MboSetRemote matRecNoInvoiceSet = this.getMboSet("MATRECNOINVOICE");
            final MboSetRemote matUseNoInvoiceSet = this.getMboSet("MATUSENOINVOICE");
            final MboSetRemote invTransNoInvoiceSet = this.getMboSet("INVTRANSNOINVOICE");
            if (!matRecNoInvoiceSet.isEmpty() || !matUseNoInvoiceSet.isEmpty() || !invTransNoInvoiceSet.isEmpty()) {
                throw new MXApplicationException("inventory", "cannotChgTransExists");
            }
            matRecNoInvoiceSet.reset();
            matUseNoInvoiceSet.reset();
            invTransNoInvoiceSet.reset();
            final MboSetRemote matRecInvoiceSet = this.getMboSet("MATRECINVOICE");
            final MboSetRemote matUseInvoiceSet = this.getMboSet("MATUSEINVOICE");
            final MboSetRemote invTransInvoiceSet = this.getMboSet("INVTRANSINVOICE");
            if (this.pendingTransactionExists(matRecInvoiceSet) || this.pendingTransactionExists(matUseInvoiceSet) || this.pendingTransactionExists(invTransInvoiceSet)) {
                throw new MXApplicationException("inventory", "cannotChgTransInvoiceExists");
            }
            matRecInvoiceSet.reset();
            matUseInvoiceSet.reset();
            invTransInvoiceSet.reset();
        }
    }
    
    public boolean checkReconcileFlag() throws MXException, RemoteException {
        final MboSetRemote invBalSet = this.getMboSet("INVBALANCES");
        MboRemote invBal = null;
        for (int index = 0; (invBal = invBalSet.getMbo(index)) != null; ++index) {
            if (!invBal.getBoolean("reconciled")) {
                return false;
            }
        }
        return true;
    }
    
    public boolean POPRExists() throws MXException, RemoteException {
        String closeCanStatus = this.getTranslator().toExternalList("POSTATUS", new String[] { "CAN", "CLOSE" });
        SqlFormat sqf = new SqlFormat(this, "status not in (" + closeCanStatus + ")" + " and siteid=:siteid and ponum in (select ponum from poline where itemnum=:itemnum and itemsetid=:itemsetid and storeloc=:1 and tositeid=:2)");
        sqf.setObject(1, "INVENTORY", "LOCATION", this.getString("location"));
        sqf.setObject(2, "INVENTORY", "SITEID", this.getString("siteid"));
        if (!this.getMboSet("$PO" + this.getString("itemnum") + this.getString("location") + this.getString("siteid") + this.getString("itemsetid"), "PO", sqf.format()).isEmpty()) {
            return true;
        }
        closeCanStatus = this.getTranslator().toExternalList("PRSTATUS", new String[] { "CAN", "COMP" });
        sqf = new SqlFormat(this, "status not in (" + closeCanStatus + ")" + " and siteid=:siteid and prnum in (select prnum from prline where itemnum=:itemnum and itemsetid=:itemsetid and storeloc=:1 and siteid=:2)");
        sqf.setObject(1, "INVENTORY", "LOCATION", this.getString("location"));
        sqf.setObject(2, "INVENTORY", "SITEID", this.getString("siteid"));
        return !this.getMboSet("$PR" + this.getString("itemnum") + this.getString("location") + this.getString("siteid") + this.getString("itemsetid"), "PR", sqf.format()).isEmpty();
    }
    
    public boolean pendingTransactionExists(final MboSetRemote transSet) throws MXException, RemoteException {
        MboRemote trans = null;
        for (int index = 0; (trans = transSet.getMbo(index)) != null; ++index) {
            final String invoicenum = trans.getString("consinvoicenum");
            final String closeCanStatus = this.getTranslator().toExternalList("IVSTATUS", new String[] { "APPR", "PAID" });
            final SqlFormat sqf = new SqlFormat(this, "invoicenum=:1 and siteid=:siteid and status not in (" + closeCanStatus + ")");
            sqf.setObject(1, "INVOICE", "INVOICENUM", invoicenum);
            if (!this.getMboSet("$INVOICE" + invoicenum + this.getString("siteid"), "INVOICE", sqf.format()).isEmpty()) {
                return true;
            }
        }
        return false;
    }
    
    public String getInvGenType() throws MXException, RemoteException {
        return this.getTranslator().toInternalString("INVGENTYPE", this.getString("invgentype"));
    }
    
    @Override
    public void invUpdateCostType() throws MXException, RemoteException {
        double unitcost = 0.0;
        String conditioncode = null;
        final ItemRemote item = (ItemRemote)this.getMboSet("ITEM").getMbo(0);
        final ItemConditionSet itemCondSet = (ItemConditionSet)item.getMboSet("ITEMCONDITION");
        MboRemote lifoFifoCost = null;
        final String costtype = this.getTranslator().toInternalString("COSTTYPE", this.getString("costtype"));
        final String newcosttype = this.getTranslator().toInternalString("COSTTYPE", this.getString("newcosttype"));
        final InvCostSetRemote invCostSet = (InvCostSetRemote)this.getMboSet("INVCOST");
        if (this.isConsignment() && !newcosttype.equalsIgnoreCase("FIFO")) {
            final Object[] params = { this.getString("itemnum") + "/" + this.getString("location") };
            this.getThisMboSet().addWarning(new MXApplicationException("inventory", "onlyFifoCost", params));
            return;
        }
        if (!costtype.equals(newcosttype)) {
            if ((costtype.equals("AVERAGE") || costtype.equals("STANDARD")) && (newcosttype.equals("LIFO") || newcosttype.equals("FIFO"))) {
                int x = 0;
                MboRemote invCostMbo = null;
                while ((invCostMbo = invCostSet.getMbo(x)) != null) {
                    if (costtype.equals("AVERAGE")) {
                        unitcost = invCostMbo.getDouble("AVGCOST");
                    }
                    else if (costtype.equals("STANDARD")) {
                        unitcost = invCostMbo.getDouble("STDCOST");
                    }
                    final double quantity = this.getMboSet("INVBALANCES").sum("curbal");
                    conditioncode = invCostMbo.getString("conditioncode");
                    if (quantity != 0.0) {
                        lifoFifoCost = this.addInvLifoFifoCostRecord(quantity, unitcost, conditioncode, null, 0L);
                        lifoFifoCost.setValue("costtype", this.getString("newcosttype"), 2L);
                    }
                    invCostMbo.delete(2L);
                    ++x;
                }
            }
            if (costtype.equals("ASSET") && (newcosttype.equals("LIFO") || newcosttype.equals("FIFO"))) {
                if (!itemCondSet.isEmpty()) {
                    int j = 0;
                    ItemCondition itemCond = null;
                    while ((itemCond = (ItemCondition)itemCondSet.getMbo(j)) != null) {
                        conditioncode = itemCond.getString("conditioncode");
                        final AssetSetRemote assetCostSet = (AssetSetRemote)this.getMboSet("ASSETINV");
                        assetCostSet.setWhere("conditioncode ='" + conditioncode + "'");
                        assetCostSet.reset();
                        if (!assetCostSet.isEmpty()) {
                            MboRemote assetCost = null;
                            int index = 0;
                            double cost = 0.0;
                            while ((assetCost = assetCostSet.getMbo(index)) != null) {
                                cost += assetCost.getDouble("invcost");
                                ++index;
                            }
                            unitcost = cost;
                            if (index > 0) {
                                unitcost = MXMath.divide(cost, index);
                            }
                        }
                        final SqlFormat Sqf = new SqlFormat(this, "itemnum = :itemnum and location = :location and siteid = :siteid and itemsetid = :itemsetid and conditioncode = :1");
                        Sqf.setObject(1, "ITEMCONDITION", "conditioncode", conditioncode);
                        final MboSetRemote invBalSet = this.getMboSet("$invBalset" + this.getString("itemnum") + this.getString("location") + conditioncode + this.getString("itemsetid") + conditioncode, "INVBALANCES", Sqf.format());
                        invBalSet.setWhere(Sqf.format());
                        final double quantity2 = invBalSet.sum("curbal");
                        if (quantity2 != 0.0) {
                            lifoFifoCost = this.addInvLifoFifoCostRecord(quantity2, unitcost, conditioncode, null, 0L);
                            lifoFifoCost.setValue("costtype", this.getString("newcosttype"), 2L);
                        }
                        ++j;
                    }
                }
                else {
                    final MboSetRemote assetCostSet2 = this.getMboSet("ASSETINV");
                    final MboRemote assetCostMbo = assetCostSet2.getMbo(0);
                    unitcost = assetCostMbo.getDouble("INVCOST");
                    final double quantity = this.getMboSet("INVBALANCES").sum("curbal");
                    conditioncode = assetCostMbo.getString("conditioncode");
                    if (quantity != 0.0) {
                        lifoFifoCost = this.addInvLifoFifoCostRecord(quantity, unitcost, conditioncode, null, 0L);
                        lifoFifoCost.setValue("costtype", this.getString("newcosttype"), 2L);
                    }
                }
            }
            if ((costtype.equals("LIFO") || costtype.equals("FIFO")) && (newcosttype.equals("AVERAGE") || newcosttype.equals("STANDARD"))) {
                if (!itemCondSet.isEmpty()) {
                    int j = 0;
                    ItemCondition itemCond = null;
                    boolean found = false;
                    while ((itemCond = (ItemCondition)itemCondSet.getMbo(j)) != null) {
                        conditioncode = itemCond.getString("conditioncode");
                        final MboSetRemote invLifoFifoSet = this.getInvLifoFifoCostRecordSet(conditioncode);
                        if (!invLifoFifoSet.isEmpty()) {
                            invCostSet.getMbo(0);
                            final MboRemote newInvCostMbo = invCostSet.add();
                            newInvCostMbo.setValue("conditioncode", conditioncode, 11L);
                            newInvCostMbo.setValue("CONDRATE", itemCond.getInt("condrate"), 11L);
                            newInvCostMbo.setValue("stdcost", ((InvLifoFifoCostSet)invLifoFifoSet).getDefaultIssueCost(), 2L);
                            if (itemCond.getInt("condrate") == 100) {
                                found = true;
                            }
                        }
                        ++j;
                    }
                    if (!found) {
                        invCostSet.getMbo(0);
                        final MboRemote newInvCostMbo2 = invCostSet.add();
                        final MboRemote itemCondition = item.getOneHundredPercent();
                        if (itemCondition != null) {
                            newInvCostMbo2.setValue("conditioncode", itemCondition.getString("conditioncode"), 11L);
                            newInvCostMbo2.setValue("condrate", itemCondition.getInt("condrate"), 11L);
                        }
                        newInvCostMbo2.setValue("stdcost", 0, 2L);
                    }
                }
                else {
                    final InvLifoFifoCostSetRemote invLifoFifoSet2 = (InvLifoFifoCostSetRemote)this.getMboSet("INVLIFOFIFOCOST");
                    final MboRemote lifoFifoCostMbo = invLifoFifoSet2.getMbo(0);
                    invCostSet.getMbo(0);
                    if (lifoFifoCostMbo != null) {
                        final MboRemote newInvCostMbo3 = invCostSet.add();
                        newInvCostMbo3.setValue("conditioncode", lifoFifoCostMbo.getString("conditioncode"), 11L);
                        newInvCostMbo3.setValue("CONDRATE", lifoFifoCostMbo.getInt("condrate"), 11L);
                        newInvCostMbo3.setValue("stdcost", ((InvLifoFifoCostSet)invLifoFifoSet2).getDefaultIssueCost(), 2L);
                    }
                    else {
                        final MboRemote newInvCostMbo3 = invCostSet.add(2L);
                        newInvCostMbo3.setValue("CONDRATE", 100, 11L);
                        newInvCostMbo3.setValue("stdcost", 0, 2L);
                    }
                }
                this.getMboSet("INVLIFOFIFOCOST").deleteAll();
            }
            if ((costtype.equals("LIFO") || costtype.equals("FIFO")) && newcosttype.equals("ASSET")) {
                this.getMboSet("INVLIFOFIFOCOST").deleteAll();
            }
            if ((costtype.equals("LIFO") || costtype.equals("FIFO")) && (newcosttype.equals("LIFO") || newcosttype.equals("FIFO"))) {
                final InvLifoFifoCostSetRemote invLifoFifoSet2 = (InvLifoFifoCostSetRemote)this.getMboSet("INVLIFOFIFOCOST");
                if (!invLifoFifoSet2.isEmpty()) {
                    for (int x2 = 0; x2 < invLifoFifoSet2.count(); ++x2) {
                        final MboRemote lifoFifoCostMbo2 = invLifoFifoSet2.getMbo(x2);
                        lifoFifoCostMbo2.setValue("costtype", this.getString("newcosttype"), 2L);
                    }
                }
            }
        }
        this.setValue("costtype", this.getString("newCostType"), 2L);
    }
    
    public void checkBalandPOPR() throws MXException, RemoteException {
        final MboSetRemote invBalSet = this.getMboSet("INVBALANCES");
        final double curbal = invBalSet.sum("curbal");
        if (curbal > 0.0) {
            throw new MXApplicationException("inventory", "cannotChgVendorBalExists");
        }
        if (!this.checkReconcileFlag()) {
            throw new MXApplicationException("inventory", "cannotChgVendorBalExists");
        }
        if (this.POPRExists()) {
            throw new MXApplicationException("inventory", "cannotChgVendorPOPRExists");
        }
        invBalSet.reset();
    }
    
    @Override
    public void setEditabilityFlags() throws MXException, RemoteException {
        if (this.isConsignment()) {
            this.setFieldFlag("manufacturer", 7L, false);
            this.setFieldFlag("modelnum", 7L, false);
            this.setFieldFlag("catalogcode", 7L, false);
            final String[] reqOnly = { "invgentype", "consvendor" };
            this.setFieldFlag(reqOnly, 128L, true);
            final String[] freqReqOnly = { "frequency", "frequnit", "nextinvoicedate" };
            if (this.getString("invgentype") != null && this.getString("invgentype").equalsIgnoreCase("FREQUENCY")) {
                this.setFieldFlag(freqReqOnly, 128L, true);
            }
        }
        else {
            final String[] readOnly = { "invgentype", "consvendor", "frequency", "frequnit", "nextinvoicedate" };
            this.setFieldFlag(readOnly, 7L, true);
            this.setFieldFlag(readOnly, 128L, false);
            if (this.getOwner() != null && this.getOwner().isBasedOn("INVENTORY")) {
                this.setFieldFlag("manufacturer", 7L, true);
                this.setFieldFlag("modelnum", 7L, true);
                this.setFieldFlag("catalogcode", 7L, true);
            }
            this.setValueNull("consvendor", 2L);
            this.setValueNull("frequency", 2L);
            this.setValueNull("nextinvoicedate", 2L);
            this.setValueNull("invgentype", 11L);
        }
    }
    
    public void increaseAccumulativeTotalCurBal(final double currentReceiptQty) throws MXException, RemoteException {
        if (Math.abs(this.accumulativeTotalCurBal + 999.99) < 1.0E-7) {
            this.accumulativeTotalCurBal = 0.0;
        }
        this.accumulativeTotalCurBal += currentReceiptQty;
    }
    
    public double getAccumulativeTotalCurBal() throws MXException, RemoteException {
        return this.accumulativeTotalCurBal;
    }
    
    protected int getNewRelationshipIndicator() throws MXException, RemoteException {
        return this.relationshipIndicator;
    }
}