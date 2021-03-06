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

package malte0811.industrialwires.blocks.controlpanel;

import malte0811.industrialwires.compat.Compat;
import malte0811.industrialwires.compat.CompatCapabilities.Charset;
import mrtjp.projectred.api.IBundledTile;
import mrtjp.projectred.api.ProjectRedAPI;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;

@Optional.Interface(iface = "mrtjp.projectred.api.IBundledTile", modid = ProjectRedAPI.modIDCore)
public class TileEntityRSPanelOthers extends TileEntityRSPanel implements IBundledTile {

	@Override
	public boolean canConnectBundled(int i) {
		return true;
	}

	@Override
	public byte[] getBundledSignal(int side) {
		byte[] ret = new byte[16];
		for (int i = 0;i<16;i++) {
			ret[i] = (byte) (17*out[i]);
		}
		return ret;
	}

	@Override
	public void updateInput() {
		byte[] data = new byte[16];
		for (EnumFacing f:EnumFacing.VALUES) {
			byte[] tmp = Compat.getBundledRS.run(world, pos, f);
			if (tmp!=null) {
				for (int i = 0; i<16; i++) {
					if (tmp[i]>data[i]) {
						data[i] = tmp[i];
					}
				}
			}
		}
		onInputChanged(data);
	}

	@Override
	protected void updateOutput() {
		Compat.updateBundledRS.run(world, pos, null);
	}

	@Nullable
	@Override
	public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
		if (capability==Charset.EMITTER_CAP) {
			return Charset.EMITTER_CAP.cast(()->Arrays.copyOf(out, 16));
		} else if (capability==Charset.RECEIVER_CAP) {
			return Charset.RECEIVER_CAP.cast(this::updateInput);
		}
		return null;
	}

	@Override
	public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
		return capability==Charset.EMITTER_CAP||capability==Charset.RECEIVER_CAP;
	}
}
