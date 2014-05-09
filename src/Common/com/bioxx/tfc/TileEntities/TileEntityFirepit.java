package com.bioxx.tfc.TileEntities;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

import com.bioxx.tfc.TFCBlocks;
import com.bioxx.tfc.TFCItems;
import com.bioxx.tfc.Core.TFC_Core;
import com.bioxx.tfc.Core.TFC_Time;
import com.bioxx.tfc.Core.Vector3f;
import com.bioxx.tfc.Items.ItemMeltedMetal;
import com.bioxx.tfc.api.HeatIndex;
import com.bioxx.tfc.api.HeatRegistry;
import com.bioxx.tfc.api.TFC_ItemHeat;
import com.bioxx.tfc.api.Enums.EnumWoodMaterial;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TileEntityFirepit extends TileEntityFireEntity implements IInventory
{
	public ItemStack fireItemStacks[];

	private int externalFireCheckTimer;
	private int externalWoodCount;
	private int oldWoodCount;
	private boolean logPileChecked;
	public int charcoalCounter;
	public boolean hasCookingPot;
	private Map<Integer, int[]> topMap = new HashMap<Integer, int[]>();
	private boolean topScan;
	private int topY;
	public final int FIREBURNTIME = (int) ((TFC_Time.hourLength * 18) / 100);//default 240

	public TileEntityFirepit()
	{
		fuelTimeLeft = 375;
		fuelBurnTemp =  613;
		fireTemp = 350;
		maxFireTempScale = 2000;
		fireItemStacks = new ItemStack[11];
		externalFireCheckTimer = 0;
		externalWoodCount = 0;
		oldWoodCount = 0;
		charcoalCounter = 0;
		hasCookingPot = false;
		topScan = true;
	}

	@Override
	public void closeInventory()
	{
	}

	public void combineMetals(ItemStack InputItem, ItemStack DestItem)
	{
		int D1 = 100-InputItem.getItemDamage();
		int D2 = 100-DestItem.getItemDamage();
		//This was causing the infinite amounts possibly
		DestItem.setItemDamage(100-(D1 + D2));
	}

	public void CookItem()
	{
		HeatRegistry manager = HeatRegistry.getInstance();
		Random R = new Random();
		if(fireItemStacks[1] != null)
		{
			HeatIndex index = manager.findMatchingIndex(fireItemStacks[1]);
			if(index != null && TFC_ItemHeat.GetTemp(fireItemStacks[1]) > index.meltTemp)
			{
				float temp = TFC_ItemHeat.GetTemp(fireItemStacks[1]);
				ItemStack output = index.getOutput(fireItemStacks[1], R);
				int damage = output.getItemDamage();
				if(output.getItem() == fireItemStacks[1].getItem())
					damage = fireItemStacks[1].getItemDamage();
				ItemStack mold = null;

				//If the input is unshaped metal
				if(fireItemStacks[1].getItem() instanceof ItemMeltedMetal)
				{
					//if both output slots are empty then just lower the input item into the first output slot
					if(fireItemStacks[7] == null && fireItemStacks[8] == null)
					{
						fireItemStacks[7] = fireItemStacks[1].copy();
						fireItemStacks[1] = null;
						return;
					}
					//Otherwise if the first output has an item that doesnt match the input item then put the item in the second output slot
					else if(fireItemStacks[7] != null && fireItemStacks[7].getItem() != TFCItems.CeramicMold && 
							(fireItemStacks[7].getItem() != fireItemStacks[1].getItem() || fireItemStacks[7].getItemDamage() == 0))
					{
						if(fireItemStacks[8] == null)
						{
							fireItemStacks[8] = fireItemStacks[1].copy();
							fireItemStacks[1] = null;
							return;
						}
					}
					mold = new ItemStack(TFCItems.CeramicMold,1);
					mold.stackSize = 1;
					mold.setItemDamage(1);
				}
				//Morph the input
				fireItemStacks[1] = index.getMorph();
				if(fireItemStacks[1] != null && manager.findMatchingIndex(fireItemStacks[1]) != null)
				{
					//if the input is a new item, then apply the old temperature to it
					TFC_ItemHeat.SetTemp(fireItemStacks[1], temp);
				}

				//Check if we should combine the output with a pre-existing output
				if(output != null && output.getItem() instanceof ItemMeltedMetal)
				{
					int leftover = 0;
					boolean addLeftover = false;
					int fromSide = 0;
					if(fireItemStacks[7] != null && output != null && output.getItem() == fireItemStacks[7].getItem() && fireItemStacks[7].getItemDamage() > 0)
					{
						int amt1 = 100-damage;//the percentage of the output
						int amt2 = 100-fireItemStacks[7].getItemDamage();//the percentage currently in the out slot
						int amt3 = amt1 + amt2;//combined amount
						leftover = amt3 - 100;//assign the leftover so that we can add to the other slot if applicable
						if(leftover > 0)
							addLeftover = true;
						int amt4 = 100-amt3;//convert the percent back to mc damage
						if(amt4 < 0)
							amt4 = 0;//stop the infinite glitch
						fireItemStacks[7] = output.copy();
						fireItemStacks[7].setItemDamage(amt4);

						TFC_ItemHeat.SetTemp(fireItemStacks[7], temp);

						if(fireItemStacks[1] == null && mold != null)
							fireItemStacks[1] = mold;
					}
					else if(fireItemStacks[8] != null && output != null && output.getItem() == fireItemStacks[8].getItem() && fireItemStacks[8].getItemDamage() > 0)
					{
						int amt1 = 100-damage;//the percentage of the output
						int amt2 = 100-fireItemStacks[8].getItemDamage();//the percentage currently in the out slot
						int amt3 = amt1 + amt2;//combined amount
						leftover = amt3 - 100;//assign the leftover so that we can add to the other slot if applicable
						if(leftover > 0)
							addLeftover = true;
						fromSide = 1;
						int amt4 = 100-amt3;//convert the percent back to mc damage
						if(amt4 < 0)
							amt4 = 0;//stop the infinite glitch
						fireItemStacks[8] = output.copy();
						fireItemStacks[8].setItemDamage(amt4);

						TFC_ItemHeat.SetTemp(fireItemStacks[8], temp);

						if(fireItemStacks[1] == null && mold != null)
							fireItemStacks[1] = mold;
					}
					else if(output != null && fireItemStacks[7] != null && fireItemStacks[7].getItem() == TFCItems.CeramicMold)
					{
						fireItemStacks[7] = output.copy();
						fireItemStacks[7].setItemDamage(damage);

						TFC_ItemHeat.SetTemp(fireItemStacks[7], temp);
					}
					else if(output != null && fireItemStacks[8] != null && fireItemStacks[8].getItem() == TFCItems.CeramicMold)
					{
						fireItemStacks[8] = output.copy();
						fireItemStacks[8].setItemDamage(damage);

						TFC_ItemHeat.SetTemp(fireItemStacks[8], temp);
					}

					if(addLeftover)
					{
						int dest = fromSide == 1 ? 7 : 8;
						if(fireItemStacks[dest] != null && output.getItem() == fireItemStacks[dest].getItem() && fireItemStacks[dest].getItemDamage() > 0)
						{
							int amt1 = 100-leftover;//the percentage of the output
							int amt2 = 100-fireItemStacks[dest].getItemDamage();//the percentage currently in the out slot
							int amt3 = amt1 + amt2;//combined amount
							int amt4 = 100-amt3;//convert the percent back to mc damage
							if(amt4 < 0)
								amt4 = 0;//stop the infinite glitch
							fireItemStacks[dest] = output.copy();
							fireItemStacks[dest].setItemDamage(amt4);

							TFC_ItemHeat.SetTemp(fireItemStacks[dest], temp);
						}
						else if(fireItemStacks[dest] != null && fireItemStacks[dest].getItem() == TFCItems.CeramicMold)
						{
							fireItemStacks[dest] = output.copy();
							fireItemStacks[dest].setItemDamage(100-leftover);
							TFC_ItemHeat.SetTemp(fireItemStacks[dest], temp);
						}
					}
				}
				else
				{
					if(fireItemStacks[7] != null && 
							fireItemStacks[7].getItem() == output.getItem() && 
							fireItemStacks[7].stackSize + output.stackSize <= fireItemStacks[7].getMaxStackSize())
					{
						fireItemStacks[7].stackSize += output.stackSize; 
					}
					else if(fireItemStacks[8] != null && 
							fireItemStacks[8].getItem() == output.getItem() && 
							fireItemStacks[8].stackSize + output.stackSize <= fireItemStacks[8].getMaxStackSize())
					{
						fireItemStacks[8].stackSize += output.stackSize; 
					}
					else if(fireItemStacks[7] == null)
					{
						fireItemStacks[7] = output.copy(); 
					}
					else if(fireItemStacks[8] == null)
					{
						fireItemStacks[8] = output.copy(); 
					}
					else if((fireItemStacks[7].stackSize == fireItemStacks[7].getMaxStackSize() && fireItemStacks[8].stackSize == fireItemStacks[8].getMaxStackSize())
							|| (fireItemStacks[7].getItem() != output.getItem() && fireItemStacks[8].getItem() != output.getItem())
							|| (fireItemStacks[7].stackSize == fireItemStacks[7].getMaxStackSize() && fireItemStacks[8].getItem() != output.getItem())
							|| (fireItemStacks[7].getItem() != output.getItem() && fireItemStacks[8].stackSize == fireItemStacks[8].getMaxStackSize()))
					{
						fireItemStacks[1] = output.copy();
					}
				}
			}
		}
	}

	@Override
	public ItemStack decrStackSize(int i, int j)
	{
		if(fireItemStacks[i] != null)
		{
			if(fireItemStacks[i].stackSize <= j)
			{
				ItemStack itemstack = fireItemStacks[i];
				fireItemStacks[i] = null;
				return itemstack;
			}
			ItemStack itemstack1 = fireItemStacks[i].splitStack(j);
			if(fireItemStacks[i].stackSize == 0)
				fireItemStacks[i] = null;
			return itemstack1;
		}
		else
			return null;
	}

	public void ejectContents()
	{
		float f3 = 0.05F;
		EntityItem entityitem;
		Random rand = new Random();
		float f = rand.nextFloat() * 0.8F + 0.1F;
		float f1 = rand.nextFloat() * 0.8F + 0.3F;
		float f2 = rand.nextFloat() * 0.8F + 0.1F;

		for (int i = 0; i < getSizeInventory(); i++)
		{
			if(fireItemStacks[i]!= null)
			{
				entityitem = new EntityItem(worldObj, xCoord + f, yCoord + f1, zCoord + f2, fireItemStacks[i]);
				entityitem.motionX = (float)rand.nextGaussian() * f3;
				entityitem.motionY = (float)rand.nextGaussian() * f3 + 0.2F;
				entityitem.motionZ = (float)rand.nextGaussian() * f3;
				worldObj.spawnEntityInWorld(entityitem);
				fireItemStacks[i] = null;
			}
		}
	}

	public void externalFireCheck()
	{
		Random R = new Random();
		if(externalFireCheckTimer == 0)
		{
			if(!logPileChecked)
			{
				logPileChecked = true;
				oldWoodCount = externalWoodCount;
				externalWoodCount = 0;
				ProcessPile(xCoord,yCoord,zCoord,false);
				if(oldWoodCount != externalWoodCount)
					charcoalCounter = 0;
			}

			//This is where we handle the counter for producing charcoal. Once it reaches 24hours, we add charcoal to the fire and remove the wood.
			if(charcoalCounter == 0)
				charcoalCounter = (int) TFC_Time.getTotalTicks();

			if(charcoalCounter > 0 && charcoalCounter + (FIREBURNTIME*100) < TFC_Time.getTotalTicks() )
			{
				logPileChecked = false;
				charcoalCounter = 0;
				ProcessPile(xCoord,yCoord,zCoord,true);
				worldObj.setBlockToAir(xCoord, yCoord, zCoord);
			}
		}
	}

	private void ProcessPile(int i, int j, int k, boolean empty)
	{
		int x = i;
		int y = 0;
		int z = k;
		boolean checkArray[][][] = new boolean[25][13][25];
		boolean reachedTop = false;

		while(!reachedTop && j+y >= 0 && y < 13)
		{
			if(worldObj.getBlock(x, j+y+1, z) != TFCBlocks.LogPile)
				reachedTop = true;
			checkOut(i, j+y, k, empty);
			scanLogs(i,j+y,k,checkArray,(byte)12,(byte)y,(byte)12, empty, false);
			y++;
		}
	}

	private boolean checkOut(int i, int j, int k, boolean empty)
	{
		if(worldObj.getBlock(i, j, k) == TFCBlocks.LogPile)
		{
			TELogPile te = (TELogPile)worldObj.getTileEntity(i, j, k);

			int count = 0;
			if(te != null)
			{
				if(!empty)
				{
					Queue<Vector3f> blocksOnFire = new ArrayDeque<Vector3f>();
					if(worldObj.isAirBlock(i+1, j, k))
						blocksOnFire.add(new Vector3f(i+1, j, k));
					if(worldObj.isAirBlock(i-1, j, k))
						blocksOnFire.add(new Vector3f(i-1, j, k));
					if(worldObj.isAirBlock(i, j, k+1))
						blocksOnFire.add(new Vector3f(i, j, k+1));
					if(worldObj.isAirBlock(i, j, k-1))
						blocksOnFire.add(new Vector3f(i, j, k-1));
					if(worldObj.isAirBlock(i, j+1, k))
						blocksOnFire.add(new Vector3f(i, j+1, k));
					if(worldObj.isAirBlock(i, j-1, k))
						blocksOnFire.add(new Vector3f(i, j-1, k));
					te.blocksToBeSetOnFire = blocksOnFire;
					te.setCharcoalFirepit(this);
				}
				else
					te.setCharcoalFirepit(null);

				if(te.storage[0] != null)
				{
					if(!empty)
						externalWoodCount += te.storage[0].stackSize;
					else
						count += te.storage[0].stackSize; te.storage[0] = null;
				}
				if(te.storage[1] != null)
				{
					if(!empty)
						externalWoodCount += te.storage[1].stackSize;
					else
						count += te.storage[1].stackSize; te.storage[1] = null;
				}
				if(te.storage[2] != null)
				{
					if(!empty)
						externalWoodCount += te.storage[2].stackSize;
					else
						count += te.storage[2].stackSize; te.storage[2] = null;
				}
				if(te.storage[3] != null)
				{
					if(!empty)
						externalWoodCount += te.storage[3].stackSize;
					else
						count += te.storage[3].stackSize; te.storage[3] = null;
				}
			}
			if(empty)
			{
				float percent = 25 + worldObj.rand.nextInt(25);
				count = (int) (count * (percent/100));
				worldObj.setBlock(i, j, k, TFCBlocks.Charcoal, count, 0x2);
				/* Trick to make the block fall or start the combining "chain" with other blocks.
				 * We don't notify the bottom block because it may be air so this block won't fall */
				 worldObj.notifyBlockOfNeighborChange(i, j, k, TFCBlocks.Charcoal);
			}
			return true;
		}
		return false;
	}

	private void scanLogs(int i, int j, int k, boolean[][][] checkArray, byte x, byte y, byte z, boolean empty, boolean top)
	{
		if(y >= 0)
		{
			checkArray[x][y][z] = true;
			int offsetX = 0;int offsetZ = 0;
			for (offsetX = -1; offsetX <= 1; offsetX++)
			{
				for (offsetZ = -1; offsetZ <= 1; offsetZ++)
				{
					if(x+offsetX < 25 && x+offsetX >= 0 && z+offsetZ < 25 && z+offsetZ >= 0 && y < 13 && y >= 0)
					{
						if(!checkArray[x+offsetX][y][z+offsetZ] && checkOut(i+offsetX, j, k+offsetZ, empty))
						{
							scanLogs(i+offsetX, j, k+offsetZ, checkArray,(byte)(x+offsetX),y,(byte)(z+offsetZ), empty, top);
							if(top)
								topMap.put(topMap.size(), new int[] {i+offsetX, j+2, k+offsetZ});
						}
					}
				}
			}
		}
	}

	public void logPileUpdate(int woodChanges)
	{
		oldWoodCount = externalWoodCount;
		externalWoodCount += woodChanges;
		if(oldWoodCount != externalWoodCount)
			charcoalCounter = 0;
	}

	@Override
	public int getInventoryStackLimit()
	{
		return 64;
	}

	@Override
	public String getInventoryName()
	{
		return "Firepit";
	}

	public float getOutput1Temp()
	{
		return TFC_ItemHeat.GetTemp(fireItemStacks[7]);
	}

	public float getOutput2Temp()
	{
		return TFC_ItemHeat.GetTemp(fireItemStacks[8]);
	}

	@Override
	public int getSizeInventory()
	{
		return fireItemStacks.length;
	}

	@Override
	public ItemStack getStackInSlot(int i)
	{
		return fireItemStacks[i];
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int var1)
	{
		return null;
	}

	public int getSurroundedByWood(int x, int y, int z)
	{
		int count = 0;
		if(worldObj.getBlock(x+1, y, z).getMaterial() == Material.wood)
			count++;
		if(worldObj.getBlock(x-1, y, z).getMaterial() == Material.wood)
			count++;
		if(worldObj.getBlock(x, y+1, z).getMaterial() == Material.wood)
			count++;
		if(worldObj.getBlock(x, y, z+1).getMaterial() == Material.wood)
			count++;
		if(worldObj.getBlock(x, y, z-1).getMaterial() == Material.wood)
			count++;
		return count;
	}

	public void HandleFuelStack()
	{
		if(fireItemStacks[3] == null && fireItemStacks[0] != null)
		{
			fireItemStacks[3] = fireItemStacks[0];
			fireItemStacks[0] = null;
		}
		if(fireItemStacks[4] == null && fireItemStacks[3] != null)
		{
			fireItemStacks[4] = fireItemStacks[3];
			fireItemStacks[3] = null;
		}
		if(fireItemStacks[5] == null && fireItemStacks[4] != null)
		{
			fireItemStacks[5] = fireItemStacks[4];
			fireItemStacks[4] = null;
		}
		else if(fireItemStacks[5] == null && fireItemStacks[4] == null)
		{
			if(worldObj.getBlock(xCoord, yCoord + 1, zCoord) == TFCBlocks.LogPile)
			{
				TELogPile te = (TELogPile)worldObj.getTileEntity(xCoord, yCoord + 1, zCoord);
				if(te.getStackInSlot(0) != null)
					fireItemStacks[5] = te.takeLog(0);
				else if(te.getStackInSlot(1) != null)
					fireItemStacks[5] = te.takeLog(1);
				else if(te.getStackInSlot(2) != null)
					fireItemStacks[5] = te.takeLog(2);
				else if(te.getStackInSlot(3) != null)
					fireItemStacks[5] = te.takeLog(3);
			}
		}
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer entityplayer)
	{
		return false;
	}

	@Override
	public void openInventory()
	{
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound)
	{
		super.readFromNBT(nbttagcompound);
		charcoalCounter = nbttagcompound.getInteger("charcoalCounter");
		externalWoodCount = nbttagcompound.getInteger("externalWoodCount");

		NBTTagList nbttaglist = nbttagcompound.getTagList("Items", 10);
		fireItemStacks = new ItemStack[getSizeInventory()];
		for(int i = 0; i < nbttaglist.tagCount(); i++)
		{
			NBTTagCompound nbttagcompound1 = (NBTTagCompound)nbttaglist.getCompoundTagAt(i);
			byte byte0 = nbttagcompound1.getByte("Slot");
			if(byte0 >= 0 && byte0 < fireItemStacks.length)
				fireItemStacks[byte0] = ItemStack.loadItemStackFromNBT(nbttagcompound1);
		}
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemstack)
	{
		fireItemStacks[i] = itemstack;
		if(itemstack != null && itemstack.stackSize > getInventoryStackLimit())
			itemstack.stackSize = getInventoryStackLimit();
	}

	@Override
	public void updateEntity()
	{
		Random R = new Random();

		int Surrounded = getSurroundedByWood(xCoord,yCoord,zCoord);
		if(fireTemp > 1 && worldObj.getBlock(xCoord, yCoord+1, zCoord) == TFCBlocks.LogPile)
		{
			externalFireCheckTimer--;
			if(externalFireCheckTimer <= 0)
			{
				if(!worldObj.isRemote)
					externalFireCheck();
				externalFireCheckTimer = 100;
			}
			
			if(R.nextInt(5) == 0)
			{
				if(worldObj.isRemote)
					GenerateSmoke();
			}
		}
		else
		{
			charcoalCounter = 0;
			logPileChecked = false;
		}

		if(!worldObj.isRemote)
		{
			//Here we take care of the item that we are cooking in the fire
			careForInventorySlot(fireItemStacks[1]);
			careForInventorySlot(fireItemStacks[7]);
			careForInventorySlot(fireItemStacks[8]);

			hasCookingPot = (fireItemStacks[1]!= null && fireItemStacks[1].getItem() == TFCItems.PotteryPot);
			updateGui();

			ItemStack[] FuelStack = new ItemStack[4];
			FuelStack[0] = fireItemStacks[0];
			FuelStack[1] = fireItemStacks[3];
			FuelStack[2] = fireItemStacks[4];
			FuelStack[3] = fireItemStacks[5];

			//Now we cook the input item
			CookItem();

			//push the input fuel down the stack
			HandleFuelStack();

			if((fireTemp < 1) && (worldObj.getBlockMetadata(xCoord, yCoord, zCoord) != 0))
			{
				worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 0, 3);
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			}
			else if((fireTemp >= 1) && (worldObj.getBlockMetadata(xCoord, yCoord, zCoord) != 1))
			{
				worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 1, 3);
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			}

			//If the fire is still burning and has fuel
			if(fuelTimeLeft > 0 && fireTemp >= 1 && Surrounded != 5)
			{
				if(worldObj.getBlockMetadata(xCoord, yCoord, zCoord) != 2)
				{
					worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 2, 3);
					worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
				}
			}
			else if(fuelTimeLeft <= 0 && fireTemp >= 1 && fireItemStacks[5] != null &&
					(!worldObj.canBlockSeeTheSky(xCoord, yCoord, zCoord) || !worldObj.isRaining()))
			{
				if(fireItemStacks[5] != null)
				{
					EnumWoodMaterial m = TFC_Core.getWoodMaterial(fireItemStacks[5]);
					fireItemStacks[5] = null;
					fuelTimeLeft = m.burnTimeMax; 
					fuelBurnTemp = m.burnTempMax;
				}
			}

			//Calculate the fire temp
			float desiredTemp = 0;
			if(Surrounded != 5)
				desiredTemp = handleTemp();
			else
				desiredTemp = 1000;

			handleTempFlux(desiredTemp);

			//Here we handle the bellows
			handleAirReduction();

			//do a last minute check to verify stack size
			if(fireItemStacks[7] != null)
			{
				if(fireItemStacks[7].stackSize <= 0)
					fireItemStacks[7].stackSize = 1;
			}

			if(fireItemStacks[8] != null)
			{
				if(fireItemStacks[8].stackSize <= 0)
					fireItemStacks[8].stackSize = 1;
			}

			if(fuelTimeLeft <= 0)
				TFC_Core.handleItemTicking(this, worldObj, xCoord, yCoord, zCoord);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound)
	{
		super.writeToNBT(nbttagcompound);
		nbttagcompound.setInteger("charcoalCounter", charcoalCounter);
		nbttagcompound.setInteger("externalWoodCount", externalWoodCount);

		NBTTagList nbttaglist = new NBTTagList();
		for(int i = 0; i < fireItemStacks.length; i++)
			if(fireItemStacks[i] != null)
			{
				NBTTagCompound nbttagcompound1 = new NBTTagCompound();
				nbttagcompound1.setByte("Slot", (byte)i);
				fireItemStacks[i].writeToNBT(nbttagcompound1);
				nbttaglist.appendTag(nbttagcompound1);
			}

		nbttagcompound.setTag("Items", nbttaglist);

	}

	@Override
	public boolean hasCustomInventoryName() 
	{
		return false;
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack) 
	{
		return false;
	}

	public boolean isInactiveCharcoalFirepit()
	{
		return logPileChecked == false && charcoalCounter == 0;
	}

	@SideOnly(Side.CLIENT)
	public void GenerateSmoke()
	{
		if(topScan)
		{
			int y=0;
			topY = yCoord;
			boolean checkArray[][][] = new boolean[25][13][25];
			while(worldObj.getBlock(xCoord, topY+1, zCoord) == TFCBlocks.LogPile)
			{
				++topY;
				++y;
			}
			topMap.put(0, new int[] {xCoord, topY+2, zCoord});
			scanLogs(xCoord,topY,zCoord,checkArray,(byte)12,(byte)y,(byte)12, false, true);
			topScan = false;
		}

		Random random = new Random();
		int sbkey = random.nextInt(topMap.size());
		int[] sb = topMap.get(sbkey);
		
		int x = sb[0];
		int y = sb[1];
		int z = sb[2];
		if(worldObj.isAirBlock(x, y, z))
		{
			float f = x + 0.5F;
			float f1 = y + 0.1F + random.nextFloat() * 6F / 16F;
			float f2 = z + 0.5F;
			float f4 = random.nextFloat() * 0.6F;
			float f5 = random.nextFloat() * -0.6F;
			worldObj.spawnParticle("smoke", f+f4 - 0.3F, f1, f2 + f5 + 0.3F, 0.0D, 0.0D, 0.0D);
			worldObj.spawnParticle("smoke", f+f4 - 0.2F, f1, f2 + f5 + 0.4F, 0.0D, 0.0D, 0.0D);
			worldObj.spawnParticle("smoke", f+f4 - 0.1F, f1, f2 + f5 + 0.1F, 0.0D, 0.0D, 0.0D);
			if(random.nextInt(10) == 0) worldObj.spawnParticle("largesmoke", f+f4 - 0.2F, f1, f2 + f5 + 0.2F, 0.0D, 0.0D, 0.0D);
		}
	}

	@Override
	public Packet getDescriptionPacket()
	{
		NBTTagCompound nbt = new NBTTagCompound();
		writeToNBT(nbt);
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt)
	{
		readFromNBT(pkt.func_148857_g());
	}

	public void updateGui()
	{
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		//validate();
	}

}