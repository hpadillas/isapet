import { Routes } from '@angular/router';
import { RegistroAfiliadoComponent } from './features/afiliados/pages/registro-afiliado/registro-afiliado.component';
import { ListaAfiliadosComponent } from './features/afiliados/pages/lista-afiliados/lista-afiliados.component';
import { DetalleAfiliadoComponent } from './features/afiliados/pages/detalle-afiliado/detalle-afiliado.component';
import { EditarAfiliadoComponent } from './features/afiliados/pages/editar-afiliado/editar-afiliado.component';
import { adminGuard } from './core/auth/admin.guard';
import { LoginComponent } from './features/auth/pages/login/login.component';
import { ListaPlanesComponent } from './features/planes/pages/lista-planes/lista-planes.component';
import { SimuladorPlanesComponent } from './features/planes/pages/simulador-planes/simulador-planes.component';
import { PlanAfiliadoComponent } from './features/planes/pages/plan-afiliado/plan-afiliado.component';
import { BonosAfiliadoComponent } from './features/bonos/pages/bonos-afiliado/bonos-afiliado.component';
import { HistorialPrestacionesComponent } from './features/prestaciones/pages/historial-prestaciones/historial-prestaciones.component';

export const routes: Routes = [
	{ path: 'login', component: LoginComponent },
	{ path: 'simulador', component: SimuladorPlanesComponent },
	{ path: 'planes', component: ListaPlanesComponent, canActivate: [adminGuard] },
	{ path: 'afiliados', pathMatch: 'full', component: ListaAfiliadosComponent, canActivate: [adminGuard] },
	{ path: 'afiliados/registro', component: RegistroAfiliadoComponent, canActivate: [adminGuard] },
	{ path: 'afiliados/:id/editar', component: EditarAfiliadoComponent, canActivate: [adminGuard] },
	{ path: 'afiliados/:id/plan', component: PlanAfiliadoComponent, canActivate: [adminGuard] },
	{ path: 'afiliados/:id/bonos', component: BonosAfiliadoComponent, canActivate: [adminGuard] },
	{ path: 'afiliados/:id/prestaciones', component: HistorialPrestacionesComponent, canActivate: [adminGuard] },
	{ path: 'afiliados/:id', component: DetalleAfiliadoComponent, canActivate: [adminGuard] },
	{ path: '', pathMatch: 'full', redirectTo: 'afiliados/registro' },
	{ path: '**', redirectTo: 'afiliados/registro' }
];
