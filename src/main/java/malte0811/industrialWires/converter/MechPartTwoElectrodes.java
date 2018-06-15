/*
 * This file is part of Industrial Wires.
 * Copyright (C) 2016-2018 malte0811
 * Industrial Wires is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Industrial Wires is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Industrial Wires.  If not, see <http://www.gnu.org/licenses/>.
 */

package malte0811.industrialWires.converter;

import com.google.common.collect.ImmutableSet;
import malte0811.industrialWires.IWConfig;
import malte0811.industrialWires.IndustrialWires;
import malte0811.industrialWires.blocks.converter.MechanicalMBBlockType;
import malte0811.industrialWires.util.ConversionUtil;
import malte0811.industrialWires.util.LocalSidedWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Set;

import static blusunrize.immersiveengineering.common.IEContent.blockMetalDecoration0;
import static blusunrize.immersiveengineering.common.blocks.metal.BlockTypes_MetalDecoration0.GENERATOR;
import static malte0811.industrialWires.converter.EUCapability.ENERGY_IC2;
import static malte0811.industrialWires.converter.IMBPartElectric.IOState.*;
import static malte0811.industrialWires.converter.Waveform.Phases.get;
import static malte0811.industrialWires.converter.Waveform.Speed.EXTERNAL;
import static malte0811.industrialWires.converter.Waveform.Type.DC;
import static malte0811.industrialWires.util.ConversionUtil.ifPerJoule;
import static malte0811.industrialWires.util.ConversionUtil.joulesPerIf;
import static malte0811.industrialWires.util.NBTKeys.*;
import static net.minecraft.util.EnumFacing.UP;
import static net.minecraft.util.math.BlockPos.ORIGIN;

public class MechPartTwoElectrodes extends MechMBPart implements IMBPartElectric {
	private double bufferToMB;
	private Waveform wfToMB = Waveform.forParameters(Waveform.Type.NONE, get(has4Phases()), Waveform.Speed.EXTERNAL);
	private double bufferToWorld;
	private Waveform wfToWorld = Waveform.forParameters(Waveform.Type.NONE, get(has4Phases()), Waveform.Speed.ROTATION);
	private IOState lastIO = NO_TRANSFER;
	private long lastStateChange = Long.MIN_VALUE;

	{
		original.put(ORIGIN, blockMetalDecoration0.getDefaultState().withProperty(
				blockMetalDecoration0.property, GENERATOR));
	}

	@Override
	public Waveform getProduced(MechEnergy state) {
		return wfToMB;
	}

	@Override
	public double getAvailableEEnergy() {
		return bufferToMB;
	}

	@Override
	public void extractEEnergy(double energy) {
		bufferToMB -= energy;
	}

	@Override
	public double requestEEnergy(Waveform waveform, MechEnergy energy) {
		if (waveform.isSinglePhase()^has4Phases()) {
			return getMaxBuffer()-bufferToWorld;
		}
		return 0;
	}

	@Override
	public void insertEEnergy(double given, Waveform waveform, MechEnergy energy) {
		bufferToWorld += given;
		wfToWorld = waveform;
	}

	@Override
	public void setLastIOState(IOState state) {
		lastIO = state;
		lastStateChange = world.getWorld().getTotalWorldTime();
	}

	@Override
	public IOState getLastIOState() {
		return lastIO;
	}

	@Override
	public void createMEnergy(MechEnergy e) {}

	@Override
	public double requestMEnergy(MechEnergy e) {
		return 0;
	}

	@Override
	public void insertMEnergy(double added) {
		if (world.getWorld().getTotalWorldTime()>lastStateChange+1) {
			setLastIOState(NO_TRANSFER);
		}
		int available = (int) (Math.min(ConversionUtil.ifPerJoule() * bufferToWorld,
				getMaxBuffer()/getEnergyConnections().size()));
		if (available > 0 && wfToWorld.isAC()) {//The IC2 net will deal with DC by itself
			bufferToWorld -= outputFE(world, available);
		}
	}

	@Override
	public double getInertia() {
		return 50;
	}

	@Override
	public double getMaxSpeed() {
		return IWConfig.MechConversion.allowMBFE()?200:-1;
	}

	@Override
	public void writeToNBT(NBTTagCompound out) {
		out.setDouble(BUFFER_IN, bufferToMB);
		out.setString(BUFFER_IN+AC, wfToMB.serializeToString());
		out.setDouble(BUFFER_OUT, bufferToWorld);
		out.setString(BUFFER_OUT+AC, wfToWorld.serializeToString());
	}

	@Override
	public void readFromNBT(NBTTagCompound in) {
		bufferToMB = in.getDouble(BUFFER_IN);
		wfToMB = Waveform.fromString(in.getString(BUFFER_IN+AC));
		bufferToWorld = in.getDouble(BUFFER_OUT);
		wfToWorld = Waveform.fromString(in.getString(BUFFER_OUT+AC));
	}

	@Override
	public ResourceLocation getRotatingBaseModel() {
		return new ResourceLocation(IndustrialWires.MODID, "block/mech_mb/shaft2.obj");
	}


	@Override
	public boolean canForm(LocalSidedWorld w) {
		if (!IWConfig.MechConversion.allowMBFE()) {
			return false;
		}
		IBlockState state = w.getBlockState(ORIGIN);
		return state.getBlock()== blockMetalDecoration0 &&
				state.getValue(blockMetalDecoration0.property)== GENERATOR;
	}

	@Override
	public short getFormPattern(int offset) {
		return 0b000_010_000;
	}

	@Override
	public void breakOnFailure(MechEnergy energy) {
		//NOP
	}

	@Override
	public MechanicalMBBlockType getType() {
		return MechanicalMBBlockType.SHAFT_1_PHASE;
	}

	private static final ImmutableSet<Pair<BlockPos, EnumFacing>> outputs = ImmutableSet.of(
			new ImmutablePair<>(ORIGIN, UP)
	);
	public Set<Pair<BlockPos, EnumFacing>> getEnergyConnections() {
		return outputs;
	}

	private IEnergyStorage energy = new IEnergyStorage() {
		@Override
		public int receiveEnergy(int maxReceive, boolean simulate) {
			if (!getLastIOState().canSwitchToInput()) {
				return 0;
			}
			double joules = joulesPerIf()*maxReceive;
			double insert = Math.min(Math.min(joules, getMaxBuffer()-bufferToMB),
					getMaxBuffer()/getEnergyConnections().size());
			if (!simulate) {
				if (!wfToMB.isAC()) {
					bufferToMB = 0;
					wfToMB = Waveform.forParameters(Waveform.Type.AC, get(has4Phases()), Waveform.Speed.EXTERNAL);
				}
				if (insert>0) {
					setLastIOState(INPUT);
				}
				bufferToMB += insert;
			}
			return (int) Math.ceil(insert* ifPerJoule());
		}

		@Override
		public int extractEnergy(int maxExtract, boolean simulate) {
			if (!getLastIOState().canSwitchToOutput()) {
				return 0;
			}
			if (wfToWorld.isAC()) {
				double joules = joulesPerIf() * maxExtract;
				double extract = Math.min(Math.min(joules, bufferToWorld), getMaxBuffer()/getEnergyConnections().size());
				if (!simulate) {
					bufferToWorld -= extract;
					if (extract>0) {
						setLastIOState(OUTPUT);
					}
				}
				return (int) Math.floor(extract * ifPerJoule());
			} else {
				return 0;
			}
		}

		@Override
		public int getEnergyStored() {
			return (int) Math.round((bufferToMB+bufferToWorld)* ifPerJoule());
		}

		@Override
		public int getMaxEnergyStored() {
			return (int) Math.round(getMaxBuffer()*2* ifPerJoule());
		}

		@Override
		public boolean canExtract() {
			return true;
		}

		@Override
		public boolean canReceive() {
			return true;
		}
	};

	@Override
	public <T> boolean hasCapability(Capability<T> cap, EnumFacing side, BlockPos pos) {
		if (getEnergyConnections().contains(new ImmutablePair<>(pos, side))) {
			if (cap==CapabilityEnergy.ENERGY)
				return true;
			if (cap==ENERGY_IC2)
				return true;
		}
		return super.hasCapability(cap, side, pos);
	}

	@Override
	public <T> T getCapability(Capability<T> cap, EnumFacing side, BlockPos pos) {
		if (getEnergyConnections().contains(new ImmutablePair<>(pos, side))) {
			if (cap == CapabilityEnergy.ENERGY)
				return CapabilityEnergy.ENERGY.cast(energy);
			if (cap==ENERGY_IC2)
				return ENERGY_IC2.cast(capIc2);
		}
		return super.getCapability(cap, side, pos);
	}

	protected double getMaxBuffer() {
		return 10e3;//200kW
	}

	protected boolean has4Phases() {
		return false;
	}

	@Override
	public AxisAlignedBB getBoundingBox(BlockPos offsetPart) {
		return new AxisAlignedBB(0, .375, 0, 1, 1, 1);
	}

	private final EUCapability.IC2EnergyHandler capIc2 = new EUCapability.IC2EnergyHandler() {
		{
			tier = 3;//TODO does this mean everything blows up?
		}

		@Override
		public double injectEnergy(EnumFacing side, double amount, double voltage) {
			double buffer = bufferToMB;
			double input = amount * ConversionUtil.joulesPerEu();
			if (!wfToMB.isDC()) {
				buffer = 0;
			}
			input = Math.min(input, getMaxBuffer()-buffer);
			buffer += input;
			bufferToMB = buffer;
			wfToMB = Waveform.forParameters(DC, get(has4Phases()), EXTERNAL);
			setLastIOState(INPUT);
			return amount-ConversionUtil.euPerJoule()*input;
		}

		@Override
		public double getOfferedEnergy() {
			if (wfToWorld.isDC() && getLastIOState().canSwitchToOutput()) {
				return Math.min(ConversionUtil.euPerJoule()*bufferToWorld,
						ConversionUtil.euPerJoule()*getMaxBuffer())/getEnergyConnections().size()*2;
			}
			return 0;
		}

		@Override
		public double getDemandedEnergy() {
			if (getLastIOState().canSwitchToInput()) {
				return Math.min(ConversionUtil.euPerJoule()*(getMaxBuffer()-bufferToMB),
						ConversionUtil.euPerJoule()*getMaxBuffer()/getEnergyConnections().size()*2);
			}
			return 0;
		}

		@Override
		public void drawEnergy(double amount) {
			bufferToWorld -= ConversionUtil.joulesPerEu()*amount;
			setLastIOState(OUTPUT);
		}
	};
}
