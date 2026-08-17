import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { LoginComponent } from './core/auth/login.component';
import { adminGuard } from './core/auth/admin.guard';

export const routes: Routes = [
    {
        path: "", // Default route
        redirectTo: "jobs/job-offer",
        pathMatch: "full",
    },
    {
        path: "login",
        component: LoginComponent,
    },
    {
        path: "jobs",
        canActivate: [authGuard],
        loadChildren: () =>
            import("./features/job-offer/job-offer.routes").then((m) => m.JOB_ROUTES),
    },
    {
        path: "admin/users",
        canActivate: [adminGuard],
        loadComponent: () =>
            import("./features/account-administration/account-administration.component")
                .then((m) => m.AccountAdministrationComponent),
    },
    {
        path: "admin/security-events",
        canActivate: [adminGuard],
        loadComponent: () =>
            import("./features/security-audit/security-audit.component")
                .then((m) => m.SecurityAuditComponent),
    },
    {
        path: "candidates",
        canActivate: [authGuard],
        loadChildren: () =>
            import("./features/cv-ingestion/cv-ingestion.routes").then((m) => m.CV_INGESTION_ROUTES),
    },
    { path: "**", redirectTo: "jobs/job-offer" },
];
